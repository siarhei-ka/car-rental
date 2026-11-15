package psn.car.rental.pricing.domain;

import java.util.Objects;

/**
 * One line of a quote.
 *
 * @param code the code of the insurance option or the extra, or the car type and class
 * @param quantity how many: always 1 except for extras
 * @param unitPrice the price of one, per day or per rental
 * @param amount the price of the line: the unit price, times the days if per day, times the
 *     quantity
 */
public record QuoteLine(
    LineKind kind,
    String code,
    String name,
    int quantity,
    Money unitPrice,
    Charge charge,
    Money amount) {

  public QuoteLine {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(unitPrice, "unitPrice");
    Objects.requireNonNull(charge, "charge");
    Objects.requireNonNull(amount, "amount");
    if (quantity < 1) {
      throw new InvalidQuoteException("A quantity is at least 1");
    }
    if (unitPrice.isNegative()) {
      throw new InvalidPriceException("A price is not negative");
    }
  }

  /** Renting the car for the days of the rental at its daily rate. */
  public static QuoteLine carRental(DailyRate rate, int days) {
    String code = rate.key().carType() + "/" + rate.key().carClass();
    return new QuoteLine(
        LineKind.CAR_RENTAL,
        code,
        "Car rental",
        1,
        rate.price(),
        Charge.PER_DAY,
        rate.forDays(days));
  }

  /** The insurance option for the days of the rental at its price per day for the driver. */
  public static QuoteLine insurance(String code, String name, Money pricePerDay, int days) {
    return new QuoteLine(
        LineKind.INSURANCE,
        code,
        name,
        1,
        pricePerDay,
        Charge.PER_DAY,
        pricePerDay.times(requireDays(days)));
  }

  /**
   * Some of an extra for the days of the rental.
   *
   * @throws InvalidQuoteException if the quantity is not from 1 to the most one reservation can
   *     have
   */
  public static QuoteLine extra(
      String code,
      String name,
      Charge charge,
      Money price,
      int maxQuantity,
      int quantity,
      int days) {
    if (quantity < 1 || quantity > maxQuantity) {
      throw new InvalidQuoteException(
          "The quantity of " + name + " is from 1 to " + maxQuantity + ", but was " + quantity);
    }
    Money one = charge == Charge.PER_DAY ? price.times(requireDays(days)) : price;
    return new QuoteLine(LineKind.EXTRA, code, name, quantity, price, charge, one.times(quantity));
  }

  /** The fixed fee for returning the car to another location of the country. */
  public static QuoteLine oneWayFee(Money fee) {
    return new QuoteLine(LineKind.ONE_WAY_FEE, null, "One-way fee", 1, fee, Charge.PER_RENTAL, fee);
  }

  private static int requireDays(int days) {
    if (days < 1) {
      throw new InvalidQuoteException("A rental lasts at least one day");
    }
    return days;
  }
}
