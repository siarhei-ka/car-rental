package psn.car.rental.discount.domain;

import java.math.BigDecimal;
import java.util.Objects;

/** What a discount rule takes off: a percentage or a fixed amount, of the extras or the total. */
public sealed interface Effect {

  Target target();

  /** The saving on an amount, never more than the amount itself. */
  Money savingOn(Money base);

  /**
   * A percentage off, from more than 0 to 100, with at most two fraction digits.
   *
   * @param percent for example 10 for 10% off
   */
  record PercentOff(BigDecimal percent, Target target) implements Effect {

    public PercentOff {
      Objects.requireNonNull(target, "target");
      if (percent == null
          || percent.signum() <= 0
          || percent.compareTo(BigDecimal.valueOf(100)) > 0
          || percent.stripTrailingZeros().scale() > 2) {
        throw new InvalidDiscountException(
            "A percentage off is more than 0 and at most 100, with at most two decimals");
      }
      percent = percent.stripTrailingZeros();
      if (percent.scale() < 0) {
        percent = percent.setScale(0);
      }
    }

    @Override
    public Money savingOn(Money base) {
      return base.percent(percent);
    }
  }

  /** A fixed amount off, in the currency of the country. It never exceeds what it is taken off. */
  record AmountOff(Money amount, Target target) implements Effect {

    public AmountOff {
      Objects.requireNonNull(target, "target");
      if (amount == null || amount.isNegative() || amount.isZero()) {
        throw new InvalidDiscountException("An amount off is more than 0");
      }
    }

    @Override
    public Money savingOn(Money base) {
      return amount.min(base);
    }
  }
}
