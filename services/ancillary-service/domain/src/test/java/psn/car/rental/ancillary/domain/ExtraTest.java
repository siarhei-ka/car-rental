package psn.car.rental.ancillary.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Extra")
class ExtraTest {

  private static Extra childSeat(boolean active) {
    return new Extra(
        "CHILD_SEAT",
        "Child seat",
        ExtraKind.EQUIPMENT,
        Charge.PER_DAY,
        Money.of("25.00", "PLN"),
        3,
        active);
  }

  @Test
  void pricesAPerDayExtraForEveryDayAndEveryPiece() {
    assertThat(childSeat(true).priceFor(2, 3)).isEqualTo(Money.of("150.00", "PLN"));
  }

  @Test
  void pricesAPerRentalExtraOnceWhateverTheLength() {
    Extra toll =
        new Extra(
            "ROAD_TOLL",
            "Road toll payment",
            ExtraKind.SERVICE,
            Charge.PER_RENTAL,
            Money.of("49", "PLN"),
            1,
            true);

    assertThat(toll.priceFor(1, 10)).isEqualTo(Money.of("49.00", "PLN"));
  }

  @Test
  void allowsOnlyQuantitiesUpToTheMaximum() {
    assertThatThrownBy(() -> childSeat(true).priceFor(4, 1))
        .isInstanceOf(InvalidPriceException.class)
        .hasMessage("The quantity of CHILD_SEAT is from 1 to 3");
    assertThatThrownBy(() -> childSeat(true).priceFor(0, 1))
        .isInstanceOf(InvalidPriceException.class);
    assertThatThrownBy(() -> childSeat(true).priceFor(1, 0))
        .isInstanceOf(InvalidPriceException.class);
  }

  @Test
  void isNotOfferedWhenSwitchedOff() {
    assertThatThrownBy(() -> childSeat(false).priceFor(1, 1))
        .isInstanceOf(InvalidPriceException.class)
        .hasMessageContaining("not offered");
  }

  @Test
  void mayBeFreeButNotNegative() {
    assertThat(
            new Extra(
                    "MAP",
                    "Map",
                    ExtraKind.EQUIPMENT,
                    Charge.PER_RENTAL,
                    Money.of("0", "EUR"),
                    1,
                    true)
                .priceFor(1, 2))
        .isEqualTo(Money.of("0", "EUR"));
    assertThatThrownBy(() ->
            new Extra(
                "MAP", "Map", ExtraKind.EQUIPMENT, Charge.PER_DAY, Money.of("-1", "EUR"), 1, true))
        .isInstanceOf(InvalidPriceException.class);
  }

  @Test
  void hasAValidCodeNameAndMaximum() {
    assertThatThrownBy(() ->
            new Extra(
                "seat", "Seat", ExtraKind.EQUIPMENT, Charge.PER_DAY, Money.of("1", "EUR"), 1, true))
        .isInstanceOf(InvalidPriceException.class);
    assertThatThrownBy(() ->
            new Extra(
                "SEAT", " ", ExtraKind.EQUIPMENT, Charge.PER_DAY, Money.of("1", "EUR"), 1, true))
        .isInstanceOf(InvalidPriceException.class);
    assertThatThrownBy(() ->
            new Extra(
                "SEAT",
                "Seat",
                ExtraKind.EQUIPMENT,
                Charge.PER_DAY,
                Money.of("1", "EUR"),
                11,
                true))
        .isInstanceOf(InvalidPriceException.class);
    assertThat(
            new Extra(
                    "SEAT",
                    "  Seat ",
                    ExtraKind.EQUIPMENT,
                    Charge.PER_DAY,
                    Money.of("1", "EUR"),
                    1,
                    true)
                .name())
        .isEqualTo("Seat");
  }

  @Test
  void keepsAmountsInTheMinorUnitsOfTheCurrency() {
    assertThat(Money.of("5", "EUR")).hasToString("5.00 EUR");
    assertThatThrownBy(() -> Money.of("1.234", "EUR")).isInstanceOf(InvalidPriceException.class);
    assertThatThrownBy(() -> Money.of("x", "EUR")).isInstanceOf(InvalidPriceException.class);
    assertThatThrownBy(() -> Money.of("1", "ABC")).isInstanceOf(InvalidPriceException.class);
  }
}
