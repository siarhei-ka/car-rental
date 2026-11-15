package psn.car.rental.ancillary.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * An ancillary product of a country, such as a child seat or road toll payment. Extras are data
 * that an administrator adds; they are never deleted, only switched off. Their number is not
 * limited by any stock.
 *
 * @param maxQuantity how many of this extra one reservation can have
 */
public record Extra(
    String code,
    String name,
    ExtraKind kind,
    Charge charge,
    Money price,
    int maxQuantity,
    boolean active) {

  public static final int MAX_QUANTITY = 10;
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public Extra {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new InvalidPriceException("An extra code is an upper-case code such as CHILD_SEAT");
    }
    if (name == null || name.isBlank() || name.length() > 100) {
      throw new InvalidPriceException("The name of an extra has 1 to 100 characters");
    }
    name = name.strip();
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(charge, "charge");
    Objects.requireNonNull(price, "price");
    if (price.isNegative()) {
      throw new InvalidPriceException("The price of an extra is not negative");
    }
    if (maxQuantity < 1 || maxQuantity > MAX_QUANTITY) {
      throw new InvalidPriceException(
          "The maximum quantity of an extra is from 1 to " + MAX_QUANTITY);
    }
  }

  /**
   * The price of some of this extra for a rental of some days.
   *
   * @throws InvalidPriceException if the quantity is not from 1 to the maximum, or the extra is
   *     switched off
   */
  public Money priceFor(int quantity, int days) {
    if (!active) {
      throw new InvalidPriceException("The extra " + code + " is not offered");
    }
    if (quantity < 1 || quantity > maxQuantity) {
      throw new InvalidPriceException("The quantity of " + code + " is from 1 to " + maxQuantity);
    }
    if (days < 1) {
      throw new InvalidPriceException("A rental lasts at least one day");
    }
    Money one = charge == Charge.PER_DAY ? price.times(days) : price;
    return one.times(quantity);
  }
}
