package psn.car.rental.pricing.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class QuoteTest {

  static final Currency PLN = Currency.getInstance("PLN");
  static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

  static Money pln(String amount) {
    return Money.of(amount, "PLN");
  }

  static final DailyRate SEDAN =
      new DailyRate(new RateKey("waw-airport", "SEDAN", "ECONOMY"), pln("149.00"));

  @Nested
  class Lines {

    @Test
    void theCarIsTheDailyRateTimesTheDays() {
      QuoteLine car = QuoteLine.carRental(SEDAN, 3);
      assertThat(car.kind()).isEqualTo(LineKind.CAR_RENTAL);
      assertThat(car.code()).isEqualTo("SEDAN/ECONOMY");
      assertThat(car.unitPrice()).isEqualTo(pln("149.00"));
      assertThat(car.amount()).isEqualTo(pln("447.00"));
    }

    @Test
    void insuranceIsItsPricePerDayTimesTheDays() {
      QuoteLine full = QuoteLine.insurance("FULL", "Full cover", pln("75.00"), 3);
      assertThat(full.amount()).isEqualTo(pln("225.00"));
      assertThat(full.charge()).isEqualTo(Charge.PER_DAY);
    }

    @Test
    void anExtraPerDayCountsTheDaysAndTheQuantity() {
      QuoteLine seats =
          QuoteLine.extra("CHILD_SEAT", "Child seat", Charge.PER_DAY, pln("25.00"), 3, 2, 3);
      assertThat(seats.amount()).isEqualTo(pln("150.00"));
      assertThat(seats.quantity()).isEqualTo(2);
    }

    @Test
    void anExtraPerRentalIgnoresTheDays() {
      QuoteLine toll =
          QuoteLine.extra("ROAD_TOLL", "Road toll", Charge.PER_RENTAL, pln("49.00"), 1, 1, 7);
      assertThat(toll.amount()).isEqualTo(pln("49.00"));
    }

    @Test
    void anExtraIsRefusedBeyondItsMaximum() {
      assertThatThrownBy(() -> QuoteLine.extra("GPS", "GPS", Charge.PER_DAY, pln("20.00"), 1, 2, 3))
          .isInstanceOf(InvalidQuoteException.class)
          .hasMessageContaining("from 1 to 1");
      assertThatThrownBy(() -> QuoteLine.extra("GPS", "GPS", Charge.PER_DAY, pln("20.00"), 1, 0, 3))
          .isInstanceOf(InvalidQuoteException.class);
    }

    @Test
    void theOneWayFeeIsOnceForTheRental() {
      QuoteLine fee = QuoteLine.oneWayFee(pln("150.00"));
      assertThat(fee.kind()).isEqualTo(LineKind.ONE_WAY_FEE);
      assertThat(fee.amount()).isEqualTo(pln("150.00"));
      assertThat(fee.charge()).isEqualTo(Charge.PER_RENTAL);
    }
  }

  @Nested
  class Totals {

    final List<QuoteLine> lines =
        List.of(
            QuoteLine.carRental(SEDAN, 3),
            QuoteLine.insurance("BASIC", "Basic cover", pln("10.00"), 3),
            QuoteLine.extra("GPS", "GPS", Charge.PER_DAY, pln("20.00"), 1, 1, 3),
            QuoteLine.oneWayFee(pln("150.00")));

    @Test
    void theTotalIsEveryLineLessTheDiscount() {
      Quote quote = new Quote(PLN, lines, Optional.of(new QuoteDiscount("A week", pln("40.85"))));

      assertThat(quote.subtotal()).isEqualTo(pln("687.00"));
      assertThat(quote.total()).isEqualTo(pln("646.15"));
      assertThat(quote.linesOf(LineKind.EXTRA)).hasSize(1);
    }

    @Test
    void withoutADiscountTheTotalIsTheSubtotal() {
      Quote quote = new Quote(PLN, lines, Optional.empty());
      assertThat(quote.total()).isEqualTo(pln("687.00"));
    }

    @Test
    void aQuoteStaysInOneCurrencyAndNeverBelowZero() {
      assertThatThrownBy(() -> new Quote(Currency.getInstance("EUR"), lines, Optional.empty()))
          .isInstanceOf(InvalidPriceException.class);
      assertThatThrownBy(
              () -> new Quote(PLN, lines, Optional.of(new QuoteDiscount("Too much", pln("700")))))
          .isInstanceOf(InvalidPriceException.class);
      assertThatThrownBy(() -> new Quote(PLN, List.of(), Optional.empty()))
          .isInstanceOf(InvalidPriceException.class);
      assertThatThrownBy(() -> new QuoteDiscount("Nothing", pln("0")))
          .isInstanceOf(InvalidPriceException.class);
      assertThatThrownBy(() -> pln("1").plus(Money.of("1", "EUR")))
          .isInstanceOf(InvalidPriceException.class);
    }
  }

  @Nested
  class Rentals {

    static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");

    @Test
    void followsTheLimitsOfAReservation() {
      new Rental(Instant.parse("2026-12-24T09:00:00Z"), 30).checkLimits(NOW, 30);
      assertThatThrownBy(() -> new Rental(NOW, 3).checkLimits(NOW, 30))
          .isInstanceOf(InvalidQuoteException.class)
          .hasMessageContaining("future");
      assertThatThrownBy(() -> new Rental(NOW.plusSeconds(60), 31).checkLimits(NOW, 30))
          .isInstanceOf(InvalidQuoteException.class);
      assertThatThrownBy(() -> new Rental(NOW.plusSeconds(60), 0).checkLimits(NOW, 30))
          .isInstanceOf(InvalidQuoteException.class);
    }

    @Test
    void theDriverAgeIsTakenOnTheLocalStartDay() {
      // 23:30 UTC on 9 December is already 10 December in Warsaw: the driver's 21st birthday.
      Rental rental = new Rental(Instant.parse("2026-12-09T23:30:00Z"), 3);
      assertThat(rental.startDay(WARSAW)).isEqualTo(LocalDate.parse("2026-12-10"));
      assertThat(rental.driverAge(LocalDate.parse("2005-12-10"), WARSAW, 21)).isEqualTo(21);

      assertThatThrownBy(() -> rental.driverAge(LocalDate.parse("2005-12-11"), WARSAW, 21))
          .isInstanceOfSatisfying(
              QuoteRefusedException.class,
              e -> assertThat(e.reason()).isEqualTo(QuoteRefusedException.Reason.DRIVER_TOO_YOUNG))
          .hasMessageContaining("at least 21");
    }
  }
}
