package psn.car.rental.reservation.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The price a reservation was made with, as the pricing service calculated it. It is stored with
 * the reservation, so later price changes do not alter it.
 *
 * @param total the amount due at pick-up
 */
public record Price(
    String currency,
    List<Line> lines,
    BigDecimal subtotal,
    Optional<Discount> discount,
    BigDecimal total) {

  public Price {
    Objects.requireNonNull(currency, "currency");
    lines = List.copyOf(lines);
    Objects.requireNonNull(subtotal, "subtotal");
    Objects.requireNonNull(discount, "discount");
    Objects.requireNonNull(total, "total");
    if (lines.isEmpty()) {
      throw new InvalidReservationException("A price has at least one line");
    }
    if (total.signum() < 0) {
      throw new InvalidReservationException("A price is not negative");
    }
  }

  /** Whether this price has the total the customer saw: the same currency and amount. */
  public boolean hasTotal(String expectedCurrency, BigDecimal expectedTotal) {
    return currency.equals(expectedCurrency) && total.compareTo(expectedTotal) == 0;
  }

  /**
   * One line of a price.
   *
   * @param kind CAR_RENTAL, INSURANCE, EXTRA or ONE_WAY_FEE
   * @param charge PER_DAY or PER_RENTAL
   */
  public record Line(
      String kind,
      String code,
      String name,
      int quantity,
      BigDecimal unitPrice,
      String charge,
      BigDecimal amount) {}

  /** The one discount rule that saved the most, and its saving. */
  public record Discount(String name, BigDecimal amount) {}
}
