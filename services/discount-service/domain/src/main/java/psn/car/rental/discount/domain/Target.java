package psn.car.rental.discount.domain;

/** What a discount is taken off. */
public enum Target {
  /** The sum of the prices of the extras. */
  EXTRAS,
  /** The total of the basket before the discount. */
  TOTAL
}
