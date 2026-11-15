package psn.car.rental.discount.domain;

import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * When a discount rule applies. A rule has at least one condition, and applies only when all of
 * them hold.
 *
 * @param minExtras the least number of extras, counted with their quantities, or null
 * @param requiredExtras the codes of extras that must all be in the basket, possibly empty
 * @param minDays the least number of rental days, or null
 */
public record Conditions(Integer minExtras, Set<String> requiredExtras, Integer minDays) {

  public static final int MAX_EXTRAS = 100;
  public static final int MAX_DAYS = 365;
  public static final int MAX_REQUIRED_EXTRAS = 10;
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public Conditions {
    requiredExtras = requiredExtras == null ? Set.of() : Set.copyOf(new TreeSet<>(requiredExtras));
    if (minExtras != null && (minExtras < 1 || minExtras > MAX_EXTRAS)) {
      throw new InvalidDiscountException("The least number of extras is from 1 to " + MAX_EXTRAS);
    }
    if (minDays != null && (minDays < 1 || minDays > MAX_DAYS)) {
      throw new InvalidDiscountException("The least number of days is from 1 to " + MAX_DAYS);
    }
    if (requiredExtras.size() > MAX_REQUIRED_EXTRAS) {
      throw new InvalidDiscountException(
          "A rule requires at most " + MAX_REQUIRED_EXTRAS + " specific extras");
    }
    for (String code : requiredExtras) {
      if (code == null || !CODE.matcher(code).matches()) {
        throw new InvalidDiscountException(
            "An extra code is an upper-case code such as CHILD_SEAT");
      }
    }
    if (minExtras == null && minDays == null && requiredExtras.isEmpty()) {
      throw new InvalidDiscountException("A rule has at least one condition");
    }
  }

  /** Whether every condition holds for the basket. */
  public boolean holdFor(Basket basket) {
    return (minExtras == null || basket.extraCount() >= minExtras)
        && (minDays == null || basket.days() >= minDays)
        && basket.extraCodes().containsAll(requiredExtras);
  }
}
