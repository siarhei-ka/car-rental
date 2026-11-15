package psn.car.rental.inventory.domain;

/** State of a hold. A released or completed hold is removed and has no state. */
public enum HoldStatus {
  /** Created, but the reservation is not stored yet. Expires if it is not confirmed in time. */
  PROVISIONAL,
  /** The reservation is stored. */
  CONFIRMED
}
