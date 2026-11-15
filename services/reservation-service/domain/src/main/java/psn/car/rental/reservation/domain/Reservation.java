package psn.car.rental.reservation.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * A customer's booking of one car of a type and class. Changes of state return a new reservation
 * together with what the inventory has to be told.
 *
 * @param customerId the customer who made the reservation: the user ID in Keycloak
 * @param price the price the reservation was made with; empty only for reservations made before
 *     prices were stored
 * @param pendingAction what the inventory still has to be told, if anything
 */
public record Reservation(
    ReservationId id,
    String customerId,
    RentalRequest rental,
    Optional<Price> price,
    ReservationState state,
    Optional<InventoryAction> pendingAction,
    Instant createdAt) {

  public Reservation {
    Objects.requireNonNull(id, "id");
    if (customerId == null || customerId.isBlank()) {
      throw new InvalidReservationException("A reservation belongs to a customer");
    }
    Objects.requireNonNull(rental, "rental");
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(state, "state");
    Objects.requireNonNull(pendingAction, "pendingAction");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * A new reservation for a held car, at the price the pricing service calculated. The hold still
   * has to be confirmed.
   *
   * @throws InvalidReservationException if the request breaks a rule of the policy
   */
  public static Reservation create(
      ReservationId id,
      String customerId,
      RentalRequest rental,
      Price price,
      ReservationPolicy policy,
      Instant now) {
    policy.check(rental, now);
    return new Reservation(
        id,
        customerId,
        rental,
        Optional.of(price),
        ReservationState.CONFIRMED,
        Optional.of(InventoryAction.CONFIRM),
        now);
  }

  /** Whether the reservation was made by this customer. */
  public boolean belongsTo(String customer) {
    return customerId.equals(customer);
  }

  /** Cancelled by the customer. Possible only before the start. */
  public Reservation cancel(Instant now) {
    requireState(ReservationState.CONFIRMED, "cancelled");
    if (!now.isBefore(rental.start())) {
      throw new InvalidStateException("A reservation can be cancelled only before its start");
    }
    return next(ReservationState.CANCELLED, InventoryAction.RELEASE);
  }

  /** The customer did not come. Possible from the start onward. */
  public Reservation markNoShow(Instant now) {
    requireState(ReservationState.CONFIRMED, "marked as a no-show");
    if (now.isBefore(rental.start())) {
      throw new InvalidStateException("A no-show can be marked only from the start onward");
    }
    return next(ReservationState.NO_SHOW, InventoryAction.RELEASE);
  }

  /** The car was handed over. */
  public Reservation pickUp() {
    requireState(ReservationState.CONFIRMED, "picked up");
    return new Reservation(
        id, customerId, rental, price, ReservationState.PICKED_UP, pendingAction, createdAt);
  }

  /** The car is back. */
  public Reservation registerReturn() {
    requireState(ReservationState.PICKED_UP, "returned");
    return next(ReservationState.RETURNED, InventoryAction.RETURN);
  }

  /** The inventory has carried out the pending action. */
  public Reservation inventoryUpdated() {
    return new Reservation(id, customerId, rental, price, state, Optional.empty(), createdAt);
  }

  /**
   * The hold expired before it could be confirmed, for example after a crash. The car is no longer
   * kept, so the reservation is cancelled.
   */
  public Reservation holdLost() {
    return new Reservation(
        id, customerId, rental, price, ReservationState.CANCELLED, Optional.empty(), createdAt);
  }

  private Reservation next(ReservationState newState, InventoryAction action) {
    return new Reservation(id, customerId, rental, price, newState, Optional.of(action), createdAt);
  }

  private void requireState(ReservationState required, String verb) {
    if (state != required) {
      throw new InvalidStateException("A reservation in state " + state + " cannot be " + verb);
    }
  }
}
