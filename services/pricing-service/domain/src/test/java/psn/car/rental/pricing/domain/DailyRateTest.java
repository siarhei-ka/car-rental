package psn.car.rental.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Daily rate")
class DailyRateTest {

  private static final RateKey KEY = new RateKey("waw-airport", "SEDAN", "ECONOMY");

  @Nested
  @DisplayName("Money")
  class Amounts {

    @Test
    void keepsTheMinorUnitsOfTheCurrency() {
      assertThat(Money.of("199", "PLN").amount()).isEqualByComparingTo("199.00");
      assertThat(Money.of("199", "PLN").amount().scale()).isEqualTo(2);
      assertThat(Money.of("1500", "JPY").amount().scale()).isZero();
      assertThat(Money.of("199.5", "EUR")).hasToString("199.50 EUR");
    }

    @Test
    void refusesMoreFractionDigitsThanTheCurrencyHas() {
      assertThatThrownBy(() -> Money.of("19.999", "PLN"))
          .isInstanceOf(InvalidPriceException.class)
          .hasMessageContaining("at most 2 fraction digits");
      assertThatThrownBy(() -> Money.of("10.5", "JPY")).isInstanceOf(InvalidPriceException.class);
      assertThat(Money.of("19.9900", "PLN").amount()).isEqualByComparingTo("19.99");
    }

    @Test
    void refusesWhatIsNoAmountOrNoCurrency() {
      assertThatThrownBy(() -> Money.of("ten", "PLN"))
          .isInstanceOf(InvalidPriceException.class)
          .hasMessageContaining("decimal number");
      assertThatThrownBy(() -> Money.of("10", "XYZ"))
          .isInstanceOf(InvalidPriceException.class)
          .hasMessageContaining("Unknown currency");
    }

    @Test
    void multipliesExactly() {
      assertThat(Money.of("0.10", "EUR").times(3).amount()).isEqualTo(new BigDecimal("0.30"));
      assertThat(Money.of("0.10", "EUR").times(3).currency())
          .isEqualTo(Currency.getInstance("EUR"));
    }
  }

  @Nested
  @DisplayName("Rates")
  class Rates {

    @Test
    void pricesARentalOfSomeDays() {
      DailyRate rate = new DailyRate(KEY, Money.of("149.90", "PLN"));

      assertThat(rate.forDays(3)).isEqualTo(Money.of("449.70", "PLN"));
      assertThatThrownBy(() -> rate.forDays(0)).isInstanceOf(InvalidPriceException.class);
    }

    @Test
    void isMoreThanZero() {
      assertThatThrownBy(() -> new DailyRate(KEY, Money.of("0", "PLN")))
          .isInstanceOf(InvalidPriceException.class)
          .hasMessageContaining("more than zero");
    }

    @Test
    void namesAValidLocationTypeAndClass() {
      assertThatThrownBy(() -> new RateKey("no spaces", "SEDAN", "ECONOMY"))
          .isInstanceOf(InvalidPriceException.class);
      assertThatThrownBy(() -> new RateKey("waw-airport", "sedan", "ECONOMY"))
          .isInstanceOf(InvalidPriceException.class);
      assertThatThrownBy(() -> new RateKey("waw-airport", "SEDAN", null))
          .isInstanceOf(InvalidPriceException.class);
    }
  }
}
