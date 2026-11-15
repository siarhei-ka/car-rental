package psn.car.rental.insurance.domain;

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
      throw new InvalidInsuranceException(
          "An amount in " + currency + " has at most " + digits + " fraction digits");
    }
    amount = amount.setScale(digits, RoundingMode.UNNECESSARY);
  }

  public static Money of(String amount, String currency) {
    try {
      return new Money(new BigDecimal(amount), Currency.getInstance(currency));
    } catch (NumberFormatException e) {
      throw new InvalidInsuranceException("An amount is a decimal number, such as 199.00");
    } catch (IllegalArgumentException e) {
      throw new InvalidInsuranceException("Unknown currency " + currency);
    }
  }

  /** This amount multiplied by a whole number, such as a number of days. */
  public Money times(int factor) {
    return new Money(amount.multiply(BigDecimal.valueOf(factor)), currency);
  }

  public boolean isPositive() {
    return amount.signum() > 0;
  }

  @Override
  public String toString() {
    return amount.toPlainString() + " " + currency.getCurrencyCode();
  }
}
