package psn.car.rental.pricing.domain;

import java.util.Objects;

/**
 * The price of one day of renting a car of a type and class at a location. There is one current
 * rate per location, type and class; a reservation keeps the price it was made with.
 */
public record DailyRate(RateKey key, Money price) {

  public DailyRate {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(price, "price");
    if (!price.isPositive()) {
      throw new InvalidPriceException("A daily rate is more than zero");
    }
  }

  /** The price of renting for a number of days. */
  public Money forDays(int days) {
    if (days < 1) {
      throw new InvalidPriceException("A rental lasts at least one day");
    }
    return price.times(days);
  }
}
