package psn.car.rental.discount.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/** Chooses the discount of a basket. Discounts do not add up: only the best rule applies. */
public final class Discounts {

  /** The largest saving first; on a tie, the rule whose name comes first, then the lower ID. */
  private static final Comparator<AppliedDiscount> BEST =
      Comparator.<AppliedDiscount, Money>comparing(AppliedDiscount::amount, Money::compareTo)
          .reversed()
          .thenComparing(d -> d.rule().name())
          .thenComparing(d -> d.rule().id());

  private Discounts() {}

  /** The rule that saves the customer the most on the day, or empty if none applies. */
  public static Optional<AppliedDiscount> best(
      Collection<DiscountRule> rules, Basket basket, LocalDate day) {
    return rules.stream().flatMap(rule -> rule.applyTo(basket, day).stream()).min(BEST);
  }
}
