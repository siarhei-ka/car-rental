package psn.car.rental.discount.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount in a currency. Amounts are decimals, never floating-point numbers, and always carry as
 * many fraction digits as the currency has minor units.
 */
public record Money(BigDecimal amount, Currency currency) {

  public Money {
    Objects.requireNonNull(amount, "amount");
    Objects.requireNonNull(currency, "currency");
    int digits = currency.getDefaultFractionDigits();
    if (amount.stripTrailingZeros().scale() > digits) {
      throw new InvalidDiscountException(
          "An amount in " + currency + " has at most " + digits + " fraction digits");
    }
    amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
  }

  public static Money of(String amount, String currency) {
    try {
      return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    } catch (NumberFormatException e) {
      throw new InvalidDiscountException("An amount is a decimal number, such as 199.00");
    } catch (IllegalArgumentException e) {
      throw new InvalidDiscountException("Unknown currency " + currency);
    }
  }

  public static Money zero(Currency currency) {
    return new Money(BigDecimal.ZERO, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(amount.add(other.amount), currency);
  }

  /** The smaller of this amount and another one in the same currency. */
  public Money min(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount) <= 0 ? this : other;
  }

  /** A percentage of this amount, rounded half up to the minor unit of the currency. */
  public Money percent(BigDecimal percent) {
    BigDecimal part =
        amount
            .multiply(percent)
            .divide(BigDecimal.valueOf(100))
            .setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    return new Money(part, currency);
  }

  public int compareTo(Money other) {
    requireSameCurrency(other);
    return amount.compareTo(other.amount);
  }

  public boolean isNegative() {
    return amount.signum() < 0;
  }

  public boolean isZero() {
    return amount.signum() == 0;
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new InvalidDiscountException(
          "Amounts in " + currency + " and " + other.currency + " cannot be combined");
    }
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency.getCurrencyCode();
  }
}
