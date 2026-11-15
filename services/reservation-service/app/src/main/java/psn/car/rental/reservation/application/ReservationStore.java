package psn.car.rental.reservation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import psn.car.rental.reservation.domain.Reservation;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Country;

/**
 * Storage of reservations. Every method runs inside the caller's transaction, after {@link
 * #useTenant(Country)} has selected the country schema.
 */
public interface ReservationStore {

  void useTenant(Country country);

  /**
   * Stores a new reservation together with the idempotency key of its request.
   *
   * @return false if a reservation with the same ID already exists
   */
  boolean insert(Reservation reservation, String idempotencyKey, String requestHash);

  /** The reservation created by an idempotency key, with the hash of its request. */
  Optional<StoredRequest> findByIdempotencyKey(String customerId, String idempotencyKey);

  Optional<Reservation> find(ReservationId id);

  /** Reads a reservation and locks it until the transaction ends. */
  Optional<Reservation> lock(ReservationId id);

  /** Stores a change. A new pending action waits from {@code now}. */
  void update(Reservation reservation, Instant now);

  void delete(ReservationId id);

  /** Reservations whose pending inventory action has waited since before the cutoff. */
  List<ReservationId> withPendingActionSince(Instant cutoff);

  /** The reservations of a customer in the current country. */
  List<Reservation> listByCustomer(String customerId);

  /** Reservations picked up at a location, ordered by start, or all when location is empty. */
  List<Reservation> list(Optional<String> pickUpLocationId);

  /** A stored reservation and the hash of the request that created it. */
  record StoredRequest(Reservation reservation, String requestHash) {}
}
