package psn.car.rental.reservation.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import psn.car.rental.reservation.config.ReservationProperties;
import psn.car.rental.reservation.domain.InventoryAction;
import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.Reservation;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.domain.ReservationPolicy;
import psn.car.rental.reservation.tenant.Countries;
import psn.car.rental.reservation.tenant.Country;
import psn.car.rental.shared.events.Actor;
import psn.car.rental.shared.events.Analytics;

/**
 * Use cases of reservations.
 *
 * <p>Calls to the inventory service are made outside database transactions. A change of state and
 * the inventory action it needs are stored together, and the action is carried out right after. If
 * the inventory does not answer, {@link InventorySyncJob} retries the action later, so an action is
 * never lost.
 */
@Service
public class ReservationService {

  private static final Logger log = LoggerFactory.getLogger(ReservationService.class);

  private final ReservationStore store;
  private final Inventory inventory;
  private final Pricing pricing;
  private final Countries countries;
  private final Analytics analytics;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final ReservationPolicy policy;
  private final ReservationProperties properties;

  public ReservationService(
      ReservationStore store,
      Inventory inventory,
      Pricing pricing,
      Countries countries,
      Analytics analytics,
      TransactionTemplate transactions,
      Clock clock,
      ReservationProperties properties) {
    this.store = store;
    this.inventory = inventory;
    this.pricing = pricing;
    this.countries = countries;
    this.analytics = analytics;
    this.transactions = transactions;
    this.clock = clock;
    this.policy = new ReservationPolicy(properties.maxDays());
    this.properties = properties;
  }

  /**
   * Creates a reservation: checks the rules, asks the pricing service for the price, holds a car,
   * stores the reservation with its price and confirms the hold.
   *
   * <p>The price is calculated again on the server; the total the customer saw is only compared
   * with it. When they differ, nothing is reserved and the customer sees the new price.
   *
   * <p>A request repeated with the same idempotency key returns the first result and creates
   * nothing new (NFR-3). The same key with a different request is rejected. Idempotency keys are
   * kept per customer.
   *
   * @param seenCurrency the currency of the total the customer saw
   * @param seenTotal the total the customer saw and confirmed
   * @throws PriceChangedException if the price is no longer the total the customer saw
   */
  public CreatedReservation create(
      Country country,
      String customerId,
      String idempotencyKey,
      RentalRequest rental,
      String seenCurrency,
      BigDecimal seenTotal) {
    String requestHash = hash(rental);
    Optional<CreatedReservation> replay = replay(country, customerId, idempotencyKey, requestHash);
    if (replay.isPresent()) {
      return replay.get();
    }

    try {
      return createNew(
          country, customerId, idempotencyKey, rental, requestHash, seenCurrency, seenTotal);
    } catch (RuntimeException e) {
      rejectionReason(e)
          .ifPresent(reason -> recordRejection(country, customerId, rental, reason, e));
      throw e;
    }
  }

  private CreatedReservation createNew(
      Country country,
      String customerId,
      String idempotencyKey,
      RentalRequest rental,
      String requestHash,
      String seenCurrency,
      BigDecimal seenTotal) {
    policy.check(rental, clock.instant());
    Price price = pricing.quote(country, customerId, rental);
    if (!price.hasTotal(seenCurrency, seenTotal)) {
      throw new PriceChangedException(price);
    }
    ReservationId id = ReservationId.forIdempotencyKey(country.code(), customerId, idempotencyKey);
    Reservation reservation =
        Reservation.create(id, customerId, rental, price, policy, clock.instant());
    inventory.placeHold(country, id, rental);

    boolean inserted =
        inTenant(country, () -> store.insert(reservation, idempotencyKey, requestHash));
    if (!inserted) {
      // A concurrent repeat of the same request stored it first. Both share the same hold.
      return replay(country, customerId, idempotencyKey, requestHash)
          .orElseThrow(() -> new ConflictingRequestException("Reservation " + id + " exists"));
    }

    try {
      inventory.confirmHold(country, id);
    } catch (InventoryUnavailableException | HoldNotFoundException e) {
      undoCreation(country, id);
      throw new InventoryUnavailableException("The reservation could not be completed", e);
    }
    return new CreatedReservation(clearPending(country, reservation), true);
  }

  public Reservation get(Country country, ReservationId id) {
    return inTenant(country, () -> store.find(id)).orElseThrow(() -> notFound(id));
  }

  /**
   * A reservation of the customer. Another customer's reservation is reported as missing, so that a
   * customer cannot learn which reservations exist.
   */
  public Reservation getOwn(Country country, ReservationId id, String customerId) {
    Reservation reservation = get(country, id);
    if (!reservation.belongsTo(customerId)) {
      throw notFound(id);
    }
    return reservation;
  }

  /** Cancelled by the customer who made it, before the start. The car is released. */
  public Reservation cancelOwn(Country country, ReservationId id, String customerId) {
    return change(
        country,
        id,
        reservation -> {
          if (!reservation.belongsTo(customerId)) {
            throw notFound(id);
          }
          return reservation.cancel(clock.instant());
        },
        "reservation.cancelled");
  }

  /** The reservations of a customer in every country, latest start first. */
  public List<OwnReservation> listOwn(String customerId) {
    return countries.all().stream()
        .flatMap(country ->
            inTenant(country, () -> store.listByCustomer(customerId)).stream()
                .map(reservation -> new OwnReservation(country, reservation)))
        .sorted(
            java.util.Comparator.comparing(
                    (OwnReservation own) -> own.reservation().rental().start())
                .reversed())
        .toList();
  }

  public List<Reservation> list(Country country, Optional<String> pickUpLocationId) {
    return inTenant(country, () -> store.list(pickUpLocationId));
  }

  /** The customer did not come. The car is released. */
  public Reservation markNoShow(Country country, ReservationId id) {
    return change(
        country, id, reservation -> reservation.markNoShow(clock.instant()), "reservation.no-show");
  }

  /** The car was handed over. */
  public Reservation pickUp(Country country, ReservationId id) {
    return change(country, id, Reservation::pickUp, "reservation.picked-up");
  }

  /** The car is back. The inventory is told, so that a one-way car joins its new location. */
  public Reservation registerReturn(Country country, ReservationId id) {
    return change(country, id, Reservation::registerReturn, "reservation.returned");
  }

  /** Reservations whose inventory action has waited longer than the retry time. */
  public List<ReservationId> pendingInventoryUpdates(Country country) {
    return inTenant(
        country,
        () ->
            store.withPendingActionSince(clock.instant().minus(properties.inventoryRetryAfter())));
  }

  /**
   * Carries out the pending inventory action of a reservation.
   *
   * @return the reservation after the attempt
   */
  public Reservation syncInventory(Country country, ReservationId id) {
    Reservation reservation = get(country, id);
    Optional<InventoryAction> action = reservation.pendingAction();
    if (action.isEmpty()) {
      return reservation;
    }
    try {
      perform(country, id, action.get());
    } catch (InventoryUnavailableException e) {
      log.warn(
          "Inventory update {} of reservation {} failed and will be retried", action.get(), id);
      return reservation;
    } catch (HoldNotFoundException e) {
      if (action.get() == InventoryAction.CONFIRM) {
        log.warn("The hold of reservation {} expired before it was confirmed", id);
        return update(
            country,
            id,
            InventoryAction.CONFIRM,
            Reservation::holdLost,
            cancelled ->
                record(
                    "reservation.cancelled",
                    country,
                    cancelled,
                    new Actor("SYSTEM", null),
                    Map.of("reason", "hold-expired")));
      }
      // The hold of a return or release is already gone, so there is nothing left to do.
    }
    return clearPending(country, reservation);
  }

  private Reservation change(
      Country country,
      ReservationId id,
      java.util.function.UnaryOperator<Reservation> step,
      String eventType) {
    Reservation changed =
        inTenant(
            country,
            () -> {
              Reservation current = store.lock(id).orElseThrow(() -> notFound(id));
              Reservation next = step.apply(current);
              store.update(next, clock.instant());
              record(eventType, country, next, Actor.current(), Map.of());
              return next;
            });
    return changed.pendingAction().isPresent() ? syncInventory(country, id) : changed;
  }

  private void perform(Country country, ReservationId id, InventoryAction action) {
    switch (action) {
      case CONFIRM -> inventory.confirmHold(country, id);
      case RELEASE -> inventory.releaseHold(country, id);
      case RETURN -> inventory.registerReturn(country, id);
    }
  }

  /**
   * Clears the pending action, unless a newer change has replaced it in the meantime. A confirmed
   * hold completes a new reservation, which is recorded as created in the same transaction.
   */
  private Reservation clearPending(Country country, Reservation done) {
    InventoryAction action = done.pendingAction().orElse(null);
    if (action == null) {
      return done;
    }
    return update(
        country,
        done.id(),
        action,
        Reservation::inventoryUpdated,
        updated -> {
          if (action == InventoryAction.CONFIRM) {
            record(
                "reservation.created",
                country,
                updated,
                Actor.customer(updated.customerId()),
                Map.of());
          }
        });
  }

  private Reservation update(
      Country country,
      ReservationId id,
      InventoryAction expectedPending,
      java.util.function.UnaryOperator<Reservation> step,
      java.util.function.Consumer<Reservation> afterChange) {
    return inTenant(
        country,
        () -> {
          Reservation current = store.lock(id).orElseThrow(() -> notFound(id));
          if (current.pendingAction().filter(expectedPending::equals).isEmpty()) {
            return current;
          }
          Reservation next = step.apply(current);
          store.update(next, clock.instant());
          afterChange.accept(next);
          return next;
        });
  }

  /** An analytics event about a reservation, in the transaction of its change. */
  private void record(
      String type,
      Country country,
      Reservation reservation,
      Actor actor,
      Map<String, Object> more) {
    Map<String, Object> data = rentalData(reservation.rental());
    data.put("reservationId", reservation.id().toString());
    data.put("customerId", reservation.customerId());
    data.put("state", reservation.state().name());
    reservation
        .price()
        .ifPresent(price -> {
          data.put(
              "total",
              Map.of("amount", price.total().toPlainString(), "currency", price.currency()));
          data.put("discount", price.discount().map(Price.Discount::name).orElse(null));
        });
    data.putAll(more);
    analytics.record(type, country.code(), actor, data);
  }

  private void recordRejection(
      Country country, String customerId, RentalRequest rental, String reason, RuntimeException e) {
    Map<String, Object> data = rentalData(rental);
    data.put("customerId", customerId);
    data.put("reason", reason);
    data.put("detail", e.getMessage());
    try {
      analytics.record("reservation.rejected", country.code(), Actor.customer(customerId), data);
    } catch (RuntimeException failure) {
      // Analytics never blocks the business: the customer still gets the reason of the refusal.
      log.warn("The rejection of a reservation could not be recorded: {}", failure.getMessage());
    }
  }

  /**
   * Why a reservation was refused, as the problem type the customer sees, or empty if it was not.
   */
  static Optional<String> rejectionReason(RuntimeException e) {
    return Optional.ofNullable(
        switch (e) {
          case psn.car.rental.reservation.domain.InvalidReservationException ignored ->
              "invalid-reservation";
          case PriceChangedException ignored -> "price-changed";
          case PricingRefusedException refused -> refused.type();
          case PricingUnavailableException ignored -> "price-unavailable";
          case NoCarAvailableException ignored -> "no-car-available";
          case InventoryUnavailableException ignored -> "inventory-unavailable";
          default -> null;
        });
  }

  private static Map<String, Object> rentalData(RentalRequest rental) {
    Map<String, Object> data = new java.util.LinkedHashMap<>();
    data.put("pickUpLocationId", rental.pickUpLocationId());
    data.put("returnLocationId", rental.returnLocationId());
    data.put("start", rental.start().toString());
    data.put("days", rental.days());
    data.put("carType", rental.carType());
    data.put("carClass", rental.carClass());
    data.put("insurance", rental.insurance());
    data.put(
        "extras",
        rental.extras().stream()
            .map(e -> Map.<String, Object>of("code", e.code(), "quantity", e.quantity()))
            .toList());
    return data;
  }

  private void undoCreation(Country country, ReservationId id) {
    inTenant(
        country,
        () -> {
          store.delete(id);
          return null;
        });
    try {
      inventory.releaseHold(country, id);
    } catch (InventoryUnavailableException e) {
      log.warn("The hold of the undone reservation {} will expire on its own", id);
    }
  }

  private Optional<CreatedReservation> replay(
      Country country, String customerId, String idempotencyKey, String requestHash) {
    return inTenant(country, () -> store.findByIdempotencyKey(customerId, idempotencyKey))
        .map(stored -> {
          if (!stored.requestHash().equals(requestHash)) {
            throw new ConflictingRequestException(
                "The idempotency key was already used for a different request");
          }
          return new CreatedReservation(stored.reservation(), false);
        });
  }

  private <T> T inTenant(Country country, Supplier<T> work) {
    return transactions.execute(status -> {
      store.useTenant(country);
      return work.get();
    });
  }

  private static ReservationNotFoundException notFound(ReservationId id) {
    return new ReservationNotFoundException("Reservation " + id + " does not exist");
  }

  /** A fingerprint of a request, to tell a repeat from a different request with the same key. */
  static String hash(RentalRequest rental) {
    String canonical =
        String.join(
            "|",
            rental.pickUpLocationId(),
            rental.returnLocationId(),
            rental.carType(),
            rental.carClass(),
            rental.start().toString(),
            Integer.toString(rental.days()),
            rental.insurance(),
            rental.extras().stream()
                .map(extra -> extra.code() + ":" + extra.quantity())
                .collect(Collectors.joining(",")));
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }

  /**
   * The result of a create request.
   *
   * @param created false when the request repeated an earlier one
   */
  public record CreatedReservation(Reservation reservation, boolean created) {}

  /** A reservation of the customer, with the country it belongs to. */
  public record OwnReservation(Country country, Reservation reservation) {}
}
