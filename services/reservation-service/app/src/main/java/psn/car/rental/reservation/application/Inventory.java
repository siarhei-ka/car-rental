package psn.car.rental.reservation.application;

import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Country;

/**
 * The inventory service, seen from reservations. Every call is safe to repeat.
 *
 * <p>Failures are reported with {@link NoCarAvailableException}, {@link
 * ConflictingRequestException}, {@link HoldNotFoundException} and {@link
 * InventoryUnavailableException}.
 */
public interface Inventory {

  /** Keeps one car for the reservation. The hold is provisional until it is confirmed. */
  void placeHold(Country country, ReservationId id, RentalRequest rental);

  void confirmHold(Country country, ReservationId id);

  /** Releasing a hold that does not exist is harmless. */
  void releaseHold(Country country, ReservationId id);

  void registerReturn(Country country, ReservationId id);
}
