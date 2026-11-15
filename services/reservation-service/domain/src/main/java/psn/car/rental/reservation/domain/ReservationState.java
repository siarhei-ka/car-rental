package psn.car.rental.reservation.domain;

/** The life cycle of a reservation. */
public enum ReservationState {
  /** The car is held and the reservation is stored. */
  CONFIRMED,
  /** Cancelled by the customer before the start. */
  CANCELLED,
  /** The car was handed over. */
  PICKED_UP,
  /** The car is back. */
  RETURNED,
  /** The customer did not come. */
  NO_SHOW
}
