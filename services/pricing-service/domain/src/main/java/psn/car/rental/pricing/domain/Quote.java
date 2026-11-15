package psn.car.rental.pricing.domain;

import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A calculated price with every line, the discount and the total. A quote reserves nothing. Every
 * amount is in the currency of the country of the pick-up location; nothing is converted.
 */
public record Quote(Currency currency, List<QuoteLine> lines, Optional<QuoteDiscount> discount) {

  public Quote {
    Objects.requireNonNull(currency, "currency");
    Objects.requireNonNull(discount, "discount");
    lines = List.copyOf(lines);
    if (lines.isEmpty()) {
      throw new InvalidPriceException("A quote has at least one line");
    }
    for (QuoteLine line : lines) {
      if (!line.amount().currency().equals(currency)) {
        throw new InvalidPriceException("Every line of a quote is in " + currency);
      }
    }
    Money subtotal = sum(currency, lines);
    discount.ifPresent(d -> {
      if (d.amount().isMoreThan(subtotal)) {
        throw new InvalidPriceException("A discount is not more than the price");
      }
    });
  }

  /** Every line added up, before the discount. */
  public Money subtotal() {
    return sum(currency, lines);
  }

  /** The amount due at pick-up. */
  public Money total() {
    Money subtotal = subtotal();
    return discount.map(d -> subtotal.minus(d.amount())).orElse(subtotal);
  }

  /** The lines of one kind. */
  public List<QuoteLine> linesOf(LineKind kind) {
    return lines.stream().filter(line -> line.kind() == kind).toList();
  }

  private static Money sum(Currency currency, List<QuoteLine> lines) {
    return lines.stream().map(QuoteLine::amount).reduce(Money.zero(currency), Money::plus);
  }
}
