package psn.car.rental.ancillary.domain;

/** How the price of an extra is counted. */
public enum Charge {
  /** The price is for each day of the rental. */
  PER_DAY,
  /** The price is for the whole rental, however long. */
  PER_RENTAL
}
