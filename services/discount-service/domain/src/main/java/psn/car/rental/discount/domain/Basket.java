package psn.car.rental.discount.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * What a customer is about to rent, as the pricing service calculated it before any discount.
 *
 * @param days the rental length
 * @param total every line of the price added up, before the discount
 * @param extras the chosen extras with the price of each line
 */
public record Basket(int days, Money total, List<Line> extras) {

  public Basket {
    Objects.requireNonNull(total, "total");
    extras = extras == null ? List.of() : List.copyOf(extras);
    if (days < 1) {
      throw new InvalidDiscountException("A rental lasts at least one day");
    }
    if (total.isNegative()) {
      throw new InvalidDiscountException("The total is not negative");
    }
    if (extras.stream().map(Line::code).distinct().count() != extras.size()) {
      throw new InvalidDiscountException("Each extra is listed once");
    }
    Money sum = Money.zero(total.currency());
    for (Line line : extras) {
      sum = sum.plus(line.price());
    }
    if (sum.compareTo(total) > 0) {
      throw new InvalidDiscountException("The extras cost more than the total");
    }
  }

  /** The number of extras, counted with their quantities: two child seats count as two. */
  public int extraCount() {
    return extras.stream().mapToInt(Line::quantity).sum();
  }

  public Set<String> extraCodes() {
    return extras.stream().map(Line::code).collect(Collectors.toUnmodifiableSet());
  }

  /** The sum of the prices of the extras. */
  public Money extrasTotal() {
    return extras.stream().map(Line::price).reduce(Money.zero(total.currency()), Money::plus);
  }

  /** The amount a discount of the target is taken off. */
  public Money base(Target target) {
    return target == Target.EXTRAS ? extrasTotal() : total;
  }

  /**
   * One extra in the basket.
   *
   * @param price the price of the line: the extra's price for the rental times the quantity
   */
  public record Line(String code, int quantity, Money price) {

    private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

    public Line {
      if (code == null || !CODE.matcher(code).matches()) {
        throw new InvalidDiscountException(
            "An extra code is an upper-case code such as CHILD_SEAT");
      }
      if (quantity < 1) {
        throw new InvalidDiscountException("The quantity of an extra is at least 1");
      }
      Objects.requireNonNull(price, "price");
      if (price.isNegative()) {
        throw new InvalidDiscountException("The price of an extra is not negative");
      }
    }
  }
}
