package psn.car.rental.reservation.domain;

/**
 * What the inventory still has to be told after a change of state. The reservation keeps it until
 * the inventory has confirmed it, so that a failed call is retried and never lost.
 */
public enum InventoryAction {
  /** Confirm the hold, because the reservation is stored. */
  CONFIRM,
  /** Release the hold, because the reservation was cancelled or the customer did not come. */
  RELEASE,
  /** Register the return of the car. */
  RETURN
}
