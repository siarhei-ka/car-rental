package psn.car.rental.discount.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import psn.car.rental.discount.domain.Effect.AmountOff;
import psn.car.rental.discount.domain.Effect.PercentOff;

class DiscountsTest {

  static final LocalDate TODAY = LocalDate.parse("2026-12-01");

  static Money pln(String amount) {
    return Money.of(amount, "PLN");
  }

  static Basket.Line extra(String code, int quantity, String price) {
    return new Basket.Line(code, quantity, pln(price));
  }

  static DiscountRule rule(String name, Conditions conditions, Effect effect) {
    return new DiscountRule(
        UUID.randomUUID(), name, conditions, effect, LocalDate.parse("2026-01-01"), null, true);
  }

  static Effect percent(String value, Target target) {
    return new PercentOff(new BigDecimal(value), target);
  }

  /** 7 days with a child seat (2 × 175.00) and road toll (49.00); total 1500.00. */
  static final Basket BASKET =
      new Basket(
          7,
          pln("1500.00"),
          List.of(extra("CHILD_SEAT", 2, "350.00"), extra("ROAD_TOLL", 1, "49.00")));

  @Nested
  class Conditions_ {

    @Test
    void countsExtrasWithTheirQuantities() {
      assertThat(new Conditions(3, null, null).holdFor(BASKET)).isTrue();
      assertThat(new Conditions(4, null, null).holdFor(BASKET)).isFalse();
    }

    @Test
    void needsEverySpecificExtra() {
      assertThat(new Conditions(null, Set.of("CHILD_SEAT", "ROAD_TOLL"), null).holdFor(BASKET))
          .isTrue();
      assertThat(new Conditions(null, Set.of("CHILD_SEAT", "GPS"), null).holdFor(BASKET)).isFalse();
    }

    @Test
    void checksTheRentalLength() {
      assertThat(new Conditions(null, null, 7).holdFor(BASKET)).isTrue();
      assertThat(new Conditions(null, null, 8).holdFor(BASKET)).isFalse();
    }

    @Test
    void appliesOnlyWhenAllHold() {
      assertThat(new Conditions(3, Set.of("ROAD_TOLL"), 7).holdFor(BASKET)).isTrue();
      assertThat(new Conditions(3, Set.of("ROAD_TOLL"), 10).holdFor(BASKET)).isFalse();
    }

    @Test
    void needAtLeastOneCondition() {
      assertThatThrownBy(() -> new Conditions(null, Set.of(), null))
          .isInstanceOf(InvalidDiscountException.class)
          .hasMessageContaining("at least one condition");
    }

    @Test
    void refuseValuesOutOfRange() {
      assertThatThrownBy(() -> new Conditions(0, null, null))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> new Conditions(null, null, 366))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> new Conditions(null, Set.of("child seat"), null))
          .isInstanceOf(InvalidDiscountException.class);
    }
  }

  @Nested
  class Effects {

    @Test
    void percentOffTheExtrasRoundsHalfUp() {
      // 12.5% of 399.00 is 49.875, rounded half up to 49.88.
      DiscountRule rule =
          rule("Extras", new Conditions(1, null, null), percent("12.5", Target.EXTRAS));
      assertThat(rule.applyTo(BASKET, TODAY))
          .get()
          .extracting(AppliedDiscount::amount)
          .isEqualTo(pln("49.88"));
    }

    @Test
    void percentOffTheTotal() {
      DiscountRule rule = rule("Week", new Conditions(null, null, 7), percent("5", Target.TOTAL));
      assertThat(rule.applyTo(BASKET, TODAY).orElseThrow().amount()).isEqualTo(pln("75.00"));
    }

    @Test
    void amountOffNeverExceedsWhatItIsTakenOff() {
      DiscountRule rule =
          rule("Big", new Conditions(1, null, null), new AmountOff(pln("500.00"), Target.EXTRAS));
      assertThat(rule.applyTo(BASKET, TODAY).orElseThrow().amount()).isEqualTo(pln("399.00"));
    }

    @Test
    void aRuleThatSavesNothingDoesNotApply() {
      Basket noExtras = new Basket(7, pln("1000.00"), List.of());
      DiscountRule rule =
          rule("Extras", new Conditions(null, null, 7), percent("10", Target.EXTRAS));
      assertThat(rule.applyTo(noExtras, TODAY)).isEmpty();
    }

    @Test
    void refuseInvalidValues() {
      assertThatThrownBy(() -> percent("0", Target.TOTAL))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> percent("100.5", Target.TOTAL))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> percent("10.123", Target.TOTAL))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> new AmountOff(pln("0"), Target.TOTAL))
          .isInstanceOf(InvalidDiscountException.class);
      assertThat(((PercentOff) percent("10.00", Target.TOTAL)).percent()).isEqualTo("10");
    }
  }

  @Nested
  class Validity {

    @Test
    void appliesOnlyWithinItsPeriodAndWhenSwitchedOn() {
      DiscountRule december =
          new DiscountRule(
              UUID.randomUUID(),
              "December",
              new Conditions(null, null, 1),
              percent("10", Target.TOTAL),
              LocalDate.parse("2026-12-01"),
              LocalDate.parse("2026-12-31"),
              true);
      assertThat(december.appliesOn(LocalDate.parse("2026-11-30"))).isFalse();
      assertThat(december.appliesOn(LocalDate.parse("2026-12-01"))).isTrue();
      assertThat(december.appliesOn(LocalDate.parse("2026-12-31"))).isTrue();
      assertThat(december.appliesOn(LocalDate.parse("2027-01-01"))).isFalse();

      DiscountRule off =
          new DiscountRule(
              december.id(),
              december.name(),
              december.conditions(),
              december.effect(),
              december.validFrom(),
              null,
              false);
      assertThat(off.applyTo(BASKET, TODAY)).isEmpty();
    }

    @Test
    void theLastDayIsNotBeforeTheFirst() {
      assertThatThrownBy(() ->
              new DiscountRule(
                  UUID.randomUUID(),
                  "Wrong",
                  new Conditions(null, null, 1),
                  percent("10", Target.TOTAL),
                  LocalDate.parse("2026-12-02"),
                  LocalDate.parse("2026-12-01"),
                  true))
          .isInstanceOf(InvalidDiscountException.class);
    }
  }

  @Nested
  class Best {

    @Test
    void onlyTheRuleThatSavesTheMostApplies() {
      DiscountRule extras =
          rule("3 extras", new Conditions(3, null, null), percent("10", Target.EXTRAS)); // 39.90
      DiscountRule week =
          rule("Week", new Conditions(null, null, 7), percent("5", Target.TOTAL)); // 75.00
      DiscountRule month =
          rule("Month", new Conditions(null, null, 30), percent("20", Target.TOTAL)); // no

      AppliedDiscount best =
          Discounts.best(List.of(extras, week, month), BASKET, TODAY).orElseThrow();

      assertThat(best.rule()).isEqualTo(week);
      assertThat(best.amount()).isEqualTo(pln("75.00"));
    }

    @Test
    void aTieGoesToTheRuleWhoseNameComesFirst() {
      DiscountRule b =
          rule("B", new Conditions(null, null, 1), new AmountOff(pln("50"), Target.TOTAL));
      DiscountRule a =
          rule("A", new Conditions(null, null, 1), new AmountOff(pln("50"), Target.TOTAL));
      assertThat(Discounts.best(List.of(b, a), BASKET, TODAY).orElseThrow().rule()).isEqualTo(a);
    }

    @Test
    void noRuleGivesNoDiscount() {
      assertThat(Discounts.best(List.of(), BASKET, TODAY)).isEmpty();
    }
  }

  @Nested
  class Baskets {

    @Test
    void refuseInconsistentBaskets() {
      assertThatThrownBy(() -> new Basket(0, pln("10"), List.of()))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> new Basket(1, pln("10"), List.of(extra("GPS", 1, "20"))))
          .isInstanceOf(InvalidDiscountException.class)
          .hasMessageContaining("more than the total");
      assertThatThrownBy(() ->
              new Basket(1, pln("100"), List.of(extra("GPS", 1, "20"), extra("GPS", 1, "20"))))
          .isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> extra("GPS", 0, "20")).isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() ->
              new Basket(1, pln("100"), List.of(new Basket.Line("GPS", 1, Money.of("5", "EUR")))))
          .isInstanceOf(InvalidDiscountException.class);
    }

    @Test
    void moneyKeepsTheMinorUnitsOfItsCurrency() {
      assertThat(pln("10").toString()).isEqualTo("10.00 PLN");
      assertThatThrownBy(() -> pln("10.001")).isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> pln("ten")).isInstanceOf(InvalidDiscountException.class);
      assertThatThrownBy(() -> Money.of("1", "XXY")).isInstanceOf(InvalidDiscountException.class);
    }
  }
}
