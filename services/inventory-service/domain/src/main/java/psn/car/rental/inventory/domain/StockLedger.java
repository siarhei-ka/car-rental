package psn.car.rental.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The cars of one car type and class at one location, and everything that uses them.
 *
 * <p>This class holds the capacity rule: at every moment, the cars in use at a location must not
 * exceed its stock.
 *
 * <ul>
 *   <li>A same-location hold uses one car during its rental period.
 *   <li>A one-way hold uses one car from its start onward, because the car does not come back.
 *   <li>A transfer uses its cars from its departure onward.
 *   <li>Cars that arrive from elsewhere count only after {@link #receiveCars(int)} is called.
 * </ul>
 *
 * <p>The class is not thread-safe. The caller loads it, changes it and stores it inside one
 * database transaction that locks the stock row.
 */
public final class StockLedger {

  private final StockKey key;
  private int stock;
  private final Map<HoldId, Hold> holds = new LinkedHashMap<>();
  private final Map<TransferId, Transfer> transfers = new LinkedHashMap<>();

  /** Creates a ledger with a number of cars and nothing using them. */
  public StockLedger(StockKey key, int stock) {
    this(key, stock, List.of(), List.of());
  }

  private StockLedger(
      StockKey key, int stock, Collection<Hold> holds, Collection<Transfer> transfers) {
    this.key = Objects.requireNonNull(key, "key");
    this.stock = requireNotNegative(stock);
    holds.forEach(hold -> this.holds.put(hold.id(), hold));
    transfers.forEach(transfer -> this.transfers.put(transfer.id(), transfer));
  }

  /** Rebuilds a ledger from stored data. */
  public static StockLedger restore(
      StockKey key, int stock, Collection<Hold> holds, Collection<Transfer> transfers) {
    return new StockLedger(key, stock, holds, transfers);
  }

  public StockKey key() {
    return key;
  }

  /** The number of cars that belong to this location now. */
  public int stock() {
    return stock;
  }

  public List<Hold> holds() {
    return List.copyOf(holds.values());
  }

  public List<Transfer> transfers() {
    return List.copyOf(transfers.values());
  }

  public Optional<Hold> findHold(HoldId id) {
    return Optional.ofNullable(holds.get(id));
  }

  /**
   * Keeps one car for a reservation.
   *
   * <p>Repeating the call with the same ID and the same details returns the existing hold.
   *
   * @throws NoCarAvailableException if no car is free for the whole period
   * @throws ConflictingRequestException if the ID is already used for a different request
   */
  public Hold placeHold(HoldId id, RentalPeriod period, LocationId returnLocation, Instant now) {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(returnLocation, "returnLocation");
    Objects.requireNonNull(now, "now");

    Hold existing = holds.get(id);
    if (existing != null) {
      if (existing.isFor(period, returnLocation)) {
        return existing;
      }
      throw new ConflictingRequestException(
          "Hold " + id.value() + " already exists with different details");
    }

    boolean oneWay = !returnLocation.equals(key.location());
    Hold hold = new Hold(id, period, returnLocation, oneWay, HoldStatus.PROVISIONAL, now);
    Instant usedUntil = hold.usedUntil().orElse(null);
    if (peakUsage(period.start(), usedUntil) + 1 > stock) {
      throw new NoCarAvailableException(
          "No car is free at " + describeKey() + " from " + period.start());
    }
    holds.put(id, hold);
    return hold;
  }

  /**
   * Marks a hold as belonging to a stored reservation. Confirming twice changes nothing.
   *
   * @throws HoldNotFoundException if the hold does not exist
   */
  public Hold confirmHold(HoldId id) {
    Hold confirmed = requireHold(id).confirmed();
    holds.put(id, confirmed);
    return confirmed;
  }

  /**
   * Gives the car back because the reservation was cancelled or the customer did not come.
   *
   * @return false if there was no such hold, so that repeating the call is harmless
   */
  public boolean releaseHold(HoldId id) {
    return holds.remove(id) != null;
  }

  /**
   * Releases provisional holds that were not confirmed in time.
   *
   * @return the IDs of the released holds
   */
  public List<HoldId> expireProvisionalHolds(Instant now, Duration timeLimit) {
    Objects.requireNonNull(now, "now");
    Objects.requireNonNull(timeLimit, "timeLimit");
    List<HoldId> expired =
        holds.values().stream()
            .filter(hold -> hold.status() == HoldStatus.PROVISIONAL)
            .filter(hold -> !hold.createdAt().plus(timeLimit).isAfter(now))
            .map(Hold::id)
            .toList();
    expired.forEach(holds::remove);
    return expired;
  }

  /**
   * Registers that the car of a hold was actually returned.
   *
   * <p>For a same-location rental the car is simply free again. For a one-way rental the car leaves
   * this stock for good, and the result says where it has arrived.
   *
   * @throws HoldNotFoundException if the hold does not exist
   */
  public Optional<Arrival> completeHold(HoldId id) {
    Hold hold = requireHold(id);
    holds.remove(id);
    if (!hold.oneWay()) {
      return Optional.empty();
    }
    stock -= 1;
    return Optional.of(new Arrival(key.at(hold.returnLocation()), 1));
  }

  /** Adds cars whose arrival at this location has been registered. */
  public void receiveCars(int cars) {
    if (cars < 1) {
      throw new IllegalArgumentException("At least 1 car must arrive, but was " + cars);
    }
    stock += cars;
  }

  /**
   * Sets the number of cars, as an administrator does.
   *
   * @throws StockInUseException if holds or transfers rely on more cars than the new number
   */
  public void changeStock(int newStock, Instant now) {
    requireNotNegative(newStock);
    Objects.requireNonNull(now, "now");
    int needed = peakUsage(now, null);
    if (newStock < needed) {
      throw new StockInUseException(
          "Stock at " + describeKey() + " cannot go below " + needed + " cars that are in use");
    }
    stock = newStock;
  }

  /**
   * Sends cars to another location.
   *
   * <p>Repeating the call with the same ID and the same details returns the existing transfer.
   *
   * @throws NoCarAvailableException if the cars are not free from the departure onward
   * @throws ConflictingRequestException if the ID is already used for a different request
   */
  public Transfer dispatchTransfer(
      TransferId id, int cars, Instant departure, LocationId target, Instant plannedArrival) {
    Transfer transfer = new Transfer(id, cars, departure, target, plannedArrival);
    if (target.equals(key.location())) {
      throw new IllegalArgumentException("A transfer must go to another location");
    }

    Transfer existing = transfers.get(id);
    if (existing != null) {
      if (existing.isSameAs(cars, departure, target)) {
        return existing;
      }
      throw new ConflictingRequestException(
          "Transfer " + id.value() + " already exists with different details");
    }

    if (peakUsage(departure, null) + cars > stock) {
      throw new NoCarAvailableException(
          cars + " cars are not free at " + describeKey() + " from " + departure);
    }
    transfers.put(id, transfer);
    return transfer;
  }

  /**
   * Registers that the cars of a transfer have arrived at their target.
   *
   * @return where the cars have arrived
   * @throws TransferNotFoundException if the transfer does not exist
   */
  public Arrival completeTransfer(TransferId id) {
    Transfer transfer = transfers.remove(id);
    if (transfer == null) {
      throw new TransferNotFoundException("Transfer " + id.value() + " does not exist");
    }
    stock -= transfer.cars();
    return new Arrival(key.at(transfer.target()), transfer.cars());
  }

  /**
   * The number of cars that could still be held for a rental.
   *
   * @param returnLocation where the car would be returned, which decides whether it comes back
   */
  public int availableCars(RentalPeriod period, LocationId returnLocation) {
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(returnLocation, "returnLocation");
    Instant usedUntil = returnLocation.equals(key.location()) ? period.end() : null;
    return Math.max(0, stock - peakUsage(period.start(), usedUntil));
  }

  /**
   * The highest number of cars in use at any moment of {@code [from, until)}.
   *
   * <p>Usage only rises at the moment something starts, so it is enough to look at the start of the
   * range and at every start inside it.
   *
   * @param until the end of the range, or null for a range without an end
   */
  private int peakUsage(Instant from, Instant until) {
    List<Usage> usages = usages();
    int peak = carsInUseAt(usages, from);
    for (Usage usage : usages) {
      boolean startsInsideRange =
          usage.from().isAfter(from) && (until == null || usage.from().isBefore(until));
      if (startsInsideRange) {
        peak = Math.max(peak, carsInUseAt(usages, usage.from()));
      }
    }
    return peak;
  }

  private static int carsInUseAt(List<Usage> usages, Instant moment) {
    return usages.stream().filter(usage -> usage.coversMoment(moment)).mapToInt(Usage::cars).sum();
  }

  private List<Usage> usages() {
    List<Usage> usages = new ArrayList<>(holds.size() + transfers.size());
    for (Hold hold : holds.values()) {
      usages.add(new Usage(hold.period().start(), hold.usedUntil().orElse(null), 1));
    }
    for (Transfer transfer : transfers.values()) {
      usages.add(new Usage(transfer.departure(), null, transfer.cars()));
    }
    return usages;
  }

  private Hold requireHold(HoldId id) {
    Hold hold = holds.get(Objects.requireNonNull(id, "id"));
    if (hold == null) {
      throw new HoldNotFoundException("Hold " + id.value() + " does not exist");
    }
    return hold;
  }

  private String describeKey() {
    return key.location().value() + " for " + key.carType().value() + " " + key.carClass().value();
  }

  private static int requireNotNegative(int stock) {
    if (stock < 0) {
      throw new IllegalArgumentException("Stock must not be negative, but was " + stock);
    }
    return stock;
  }

  /**
   * Cars in use during {@code [from, until)}.
   *
   * @param until the first moment the cars are free again, or null if they never come back
   */
  private record Usage(Instant from, Instant until, int cars) {

    boolean coversMoment(Instant moment) {
      return !from.isAfter(moment) && (until == null || moment.isBefore(until));
    }
  }
}
