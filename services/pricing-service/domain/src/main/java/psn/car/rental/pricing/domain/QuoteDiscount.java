package psn.car.rental.pricing.domain;

import java.util.Objects;

/** The discount of a quote: the one rule that saves the customer the most, and its saving. */
public record QuoteDiscount(String name, Money amount) {

  public QuoteDiscount {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(amount, "amount");
    if (!amount.isPositive()) {
      throw new InvalidPriceException("A discount is more than zero");
    }
  }
}
