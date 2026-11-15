package psn.car.rental.inventory.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psn.car.rental.inventory.config.InventoryProperties;
import psn.car.rental.inventory.domain.Arrival;
import psn.car.rental.inventory.domain.ConflictingRequestException;
import psn.car.rental.inventory.domain.Hold;
import psn.car.rental.inventory.domain.HoldId;
import psn.car.rental.inventory.domain.HoldNotFoundException;
import psn.car.rental.inventory.domain.HoldStatus;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.NoCarAvailableException;
import psn.car.rental.inventory.domain.RentalPeriod;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.domain.StockLedger;
import psn.car.rental.inventory.domain.Transfer;
import psn.car.rental.inventory.domain.TransferId;
import psn.car.rental.inventory.domain.TransferNotFoundException;
import psn.car.rental.inventory.tenant.Country;
import psn.car.rental.shared.events.Analytics;

/**
 * Use cases of the inventory. Each method is one transaction that locks the stock rows it changes,
 * applies the domain rule and stores the result.
 */
@Service
@Transactional
public class InventoryService {

  private static final Comparator<StockKey> LOCK_ORDER =
      Comparator.comparing((StockKey key) -> key.location().value())
          .thenComparing(key -> key.carType().value())
          .thenComparing(key -> key.carClass().value());

  private final LedgerStore store;
  private final Clock clock;
  private final InventoryProperties properties;
  private final Analytics analytics;

  public InventoryService(
      LedgerStore store, Clock clock, InventoryProperties properties, Analytics analytics) {
    this.store = store;
    this.clock = clock;
    this.properties = properties;
    this.analytics = analytics;
  }

  /** Keeps one car for a reservation, or returns the existing hold for a repeated request. */
  public PlacedHold placeHold(
      Country country, StockKey key, HoldId id, RentalPeriod period, LocationId returnLocation) {
    store.useTenant(country);
    store
        .findHold(id)
        .filter(existing -> !existing.key().equals(key))
        .ifPresent(existing -> {
          throw new ConflictingRequestException(
              "Hold " + id.value() + " already exists for other cars");
        });
    StockLedger ledger =
        lock(key)
            .orElseThrow(() ->
                new NoCarAvailableException("There are no cars of this kind at this location"));
    boolean created = ledger.findHold(id).isEmpty();
    Hold hold = ledger.placeHold(id, period, returnLocation, clock.instant());
    if (created) {
      store.insertHold(key, hold);
    }
    return new PlacedHold(key, hold, created);
  }

  /** Marks a hold as belonging to a stored reservation. */
  public PlacedHold confirmHold(Country country, HoldId id) {
    store.useTenant(country);
    StockKey key = requireHold(id).key();
    StockLedger ledger = lock(key).orElseThrow(() -> holdNotFound(id));
    Hold hold = ledger.confirmHold(id);
    store.updateHoldStatus(id, HoldStatus.CONFIRMED);
    return new PlacedHold(key, hold, false);
  }

  /**
   * Gives the car back after a cancellation or a no-show.
   *
   * @return false if there was no such hold
   */
  public boolean releaseHold(Country country, HoldId id) {
    store.useTenant(country);
    Optional<LedgerStore.HoldRef> ref = store.findHold(id);
    if (ref.isEmpty()) {
      return false;
    }
    StockLedger ledger = lock(ref.get().key()).orElseThrow(() -> holdNotFound(id));
    boolean released = ledger.releaseHold(id);
    if (released) {
      store.deleteHolds(List.of(id));
    }
    return released;
  }

  /**
   * Registers that the car of a hold was returned. For a one-way rental the car joins the stock of
   * the return location.
   */
  public void registerReturn(Country country, HoldId id) {
    store.useTenant(country);
    LedgerStore.HoldRef ref = requireHold(id);
    StockKey source = ref.key();
    StockKey target = source.at(ref.returnLocation());
    Map<StockKey, StockLedger> ledgers = lockAll(source, target);
    StockLedger ledger = ledgers.get(source);
    Optional<Arrival> arrival = ledger.completeHold(id);
    store.deleteHolds(List.of(id));
    arrival.ifPresent(moved -> {
      store.updateStock(source, ledger.stock());
      receive(ledgers.get(moved.destination()), moved);
    });
  }

  /** Sets the number of cars of a type and class at a location. */
  public StockLedger changeStock(Country country, StockKey key, int cars) {
    store.useTenant(country);
    store.ensureStock(key);
    StockLedger ledger = lock(key).orElseThrow();
    ledger.changeStock(cars, clock.instant());
    store.updateStock(key, ledger.stock());
    analytics.record("admin.stock-changed", country.code(), stockData(key, Map.of("cars", cars)));
    return ledger;
  }

  /** Sends cars to another location of the same country. */
  public PlacedTransfer dispatchTransfer(
      Country country,
      StockKey key,
      TransferId id,
      int cars,
      Instant departure,
      LocationId target,
      Instant plannedArrival) {
    store.useTenant(country);
    store
        .findTransfer(id)
        .filter(existing -> !existing.key().equals(key))
        .ifPresent(existing -> {
          throw new ConflictingRequestException(
              "Transfer " + id.value() + " already exists for other cars");
        });
    StockLedger ledger =
        lock(key)
            .orElseThrow(() ->
                new NoCarAvailableException("There are no cars of this kind at this location"));
    boolean created = ledger.transfers().stream().noneMatch(t -> t.id().equals(id));
    Transfer transfer = ledger.dispatchTransfer(id, cars, departure, target, plannedArrival);
    if (created) {
      store.insertTransfer(key, transfer);
      analytics.record(
          "admin.transfer-dispatched",
          country.code(),
          stockData(
              key,
              Map.of(
                  "transferId", id.value(),
                  "cars", cars,
                  "targetLocationId", target.value(),
                  "departure", departure.toString(),
                  "plannedArrival", plannedArrival.toString())));
    }
    return new PlacedTransfer(key, transfer, created);
  }

  /** Registers that the cars of a transfer arrived, and adds them to the target stock. */
  public void registerArrival(Country country, TransferId id) {
    store.useTenant(country);
    LedgerStore.TransferRef ref =
        store
            .findTransfer(id)
            .orElseThrow(
                () -> new TransferNotFoundException("Transfer " + id.value() + " does not exist"));
    StockKey source = ref.key();
    Map<StockKey, StockLedger> ledgers = lockAll(source, source.at(ref.target()));
    StockLedger ledger = ledgers.get(source);
    Arrival arrival = ledger.completeTransfer(id);
    store.deleteTransfer(id);
    store.updateStock(source, ledger.stock());
    receive(ledgers.get(arrival.destination()), arrival);
    analytics.record(
        "admin.transfer-arrived",
        country.code(),
        stockData(
            source, Map.of("transferId", id.value(), "targetLocationId", ref.target().value())));
  }

  private static Map<String, Object> stockData(StockKey key, Map<String, Object> more) {
    Map<String, Object> data = new java.util.LinkedHashMap<>();
    data.put("locationId", key.location().value());
    data.put("carType", key.carType().value());
    data.put("carClass", key.carClass().value());
    data.putAll(more);
    return data;
  }

  /** How many cars of each type and class at a location could be held for a rental. */
  @Transactional(readOnly = true)
  public List<AvailableCars> availability(
      Country country, LocationId location, RentalPeriod period, LocationId returnLocation) {
    store.useTenant(country);
    return store.listStock(Optional.of(location)).stream()
        .map(row -> store.load(row.key(), row.cars()))
        .map(
            ledger -> new AvailableCars(ledger.key(), ledger.availableCars(period, returnLocation)))
        .toList();
  }

  /** The stock rows of a location, or of all locations of the country. */
  @Transactional(readOnly = true)
  public List<LedgerStore.StockRow> listStock(Country country, Optional<LocationId> location) {
    store.useTenant(country);
    return store.listStock(location);
  }

  /** Transfers that have not arrived, from one location or from all of the country. */
  @Transactional(readOnly = true)
  public List<LedgerStore.PendingTransfer> listTransfers(
      Country country, Optional<LocationId> from) {
    store.useTenant(country);
    return store.listTransfers(from);
  }

  /** Stock rows that have provisional holds older than the time limit. */
  @Transactional(readOnly = true)
  public List<StockKey> stockWithExpiredHolds(Country country) {
    store.useTenant(country);
    return store.keysWithProvisionalHoldsBefore(clock.instant().minus(properties.holdTimeLimit()));
  }

  /**
   * Releases the provisional holds of one stock row that were not confirmed in time.
   *
   * @return the released holds
   */
  public List<HoldId> expireHolds(Country country, StockKey key) {
    store.useTenant(country);
    Optional<StockLedger> ledger = lock(key);
    if (ledger.isEmpty()) {
      return List.of();
    }
    List<HoldId> expired =
        ledger.get().expireProvisionalHolds(clock.instant(), properties.holdTimeLimit());
    store.deleteHolds(expired);
    return expired;
  }

  private void receive(StockLedger target, Arrival arrival) {
    target.receiveCars(arrival.cars());
    store.updateStock(target.key(), target.stock());
  }

  private Optional<StockLedger> lock(StockKey key) {
    return store.lockStock(key).map(stock -> store.load(key, stock));
  }

  /**
   * Locks several stock rows in a fixed order, so that two transactions that need the same rows
   * cannot wait for each other forever.
   */
  private Map<StockKey, StockLedger> lockAll(StockKey... keys) {
    Map<StockKey, StockLedger> ledgers = new HashMap<>();
    Stream.of(keys)
        .distinct()
        .sorted(LOCK_ORDER)
        .forEach(key -> {
          store.ensureStock(key);
          ledgers.put(key, lock(key).orElseThrow());
        });
    return ledgers;
  }

  private LedgerStore.HoldRef requireHold(HoldId id) {
    return store.findHold(id).orElseThrow(() -> holdNotFound(id));
  }

  private static HoldNotFoundException holdNotFound(HoldId id) {
    return new HoldNotFoundException("Hold " + id.value() + " does not exist");
  }
}
