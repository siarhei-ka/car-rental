package psn.car.rental.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Reservation")
class ReservationTest {

  private static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");
  private static final Instant START = Instant.parse("2026-12-10T10:00:00Z");
  private static final ReservationPolicy POLICY = new ReservationPolicy(30);
  private static final ReservationId ID =
      ReservationId.forIdempotencyKey("pl", "customer-1", "key-1");

  private static final Price PRICE =
      new Price(
          "PLN",
          List.of(
              new Price.Line(
                  "CAR_RENTAL",
                  "SEDAN/ECONOMY",
                  "Car rental",
                  1,
                  new BigDecimal("149.00"),
                  "PER_DAY",
                  new BigDecimal("447.00"))),
          new BigDecimal("447.00"),
          Optional.empty(),
          new BigDecimal("447.00"));

  private static RentalRequest rental(Instant start, int days) {
    return new RentalRequest("waw-airport", "waw-airport", "SEDAN", "ECONOMY", start, days);
  }

  private static Reservation confirmed() {
    return Reservation.create(ID, "customer-1", rental(START, 3), PRICE, POLICY, NOW)
        .inventoryUpdated();
  }

  @Nested
  @DisplayName("Rules for a new reservation")
  class Rules {

    @Test
    void belongsToTheCustomerWhoMadeIt() {
      Reservation created =
          Reservation.create(ID, "customer-1", rental(START, 3), PRICE, POLICY, NOW);

      assertThat(created.belongsTo("customer-1")).isTrue();
      assertThat(created.belongsTo("customer-2")).isFalse();
      assertThat(created.cancel(NOW).belongsTo("customer-1")).isTrue();
    }

    @Test
    void needsACustomer() {
      assertThatThrownBy(() -> Reservation.create(ID, " ", rental(START, 3), PRICE, POLICY, NOW))
          .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void createsAConfirmedReservationWhoseHoldMustBeConfirmed() {
      Reservation created =
          Reservation.create(ID, "customer-1", rental(START, 3), PRICE, POLICY, NOW);

      assertThat(created.state()).isEqualTo(ReservationState.CONFIRMED);
      assertThat(created.pendingAction()).contains(InventoryAction.CONFIRM);
      assertThat(created.createdAt()).isEqualTo(NOW);
      assertThat(created.rental().end()).isEqualTo(Instant.parse("2026-12-13T10:00:00Z"));
    }

    @Test
    void startMustBeInTheFuture() {
      assertThatThrownBy(
              () -> Reservation.create(ID, "customer-1", rental(NOW, 1), PRICE, POLICY, NOW))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() ->
              Reservation.create(
                  ID, "customer-1", rental(NOW.minusSeconds(1), 1), PRICE, POLICY, NOW))
          .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void rentalLastsFromOneDayToTheMaximum() {
      assertThat(
              Reservation.create(ID, "customer-1", rental(START, 1), PRICE, POLICY, NOW)
                  .rental()
                  .days())
          .isOne();
      assertThat(
              Reservation.create(ID, "customer-1", rental(START, 30), PRICE, POLICY, NOW)
                  .rental()
                  .days())
          .isEqualTo(30);
      assertThatThrownBy(
              () -> Reservation.create(ID, "customer-1", rental(START, 0), PRICE, POLICY, NOW))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(
              () -> Reservation.create(ID, "customer-1", rental(START, 31), PRICE, POLICY, NOW))
          .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void maximumLengthIsConfiguration() {
      ReservationPolicy shortRentals = new ReservationPolicy(7);

      assertThatThrownBy(() ->
              Reservation.create(ID, "customer-1", rental(START, 8), PRICE, shortRentals, NOW))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() -> new ReservationPolicy(0))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidLocationsAndCodes() {
      assertThatThrownBy(() -> new RentalRequest("", "b", "SEDAN", "ECONOMY", START, 1))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() -> new RentalRequest("a", "b c", "SEDAN", "ECONOMY", START, 1))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() -> new RentalRequest("a", "b", "sedan", "ECONOMY", START, 1))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() -> new RentalRequest("a", "b", "SEDAN", null, START, 1))
          .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void oneWayWhenTheReturnLocationDiffers() {
      assertThat(rental(START, 1).oneWay()).isFalse();
      assertThat(new RentalRequest("a", "b", "SUV", "ECONOMY", START, 1).oneWay()).isTrue();
    }
  }

  @Nested
  @DisplayName("Life cycle")
  class LifeCycle {

    @Test
    void cancellationBeforeTheStartReleasesTheCar() {
      Reservation cancelled = confirmed().cancel(START.minusSeconds(1));

      assertThat(cancelled.state()).isEqualTo(ReservationState.CANCELLED);
      assertThat(cancelled.pendingAction()).contains(InventoryAction.RELEASE);
    }

    @Test
    void cannotBeCancelledFromTheStartOnward() {
      assertThatThrownBy(() -> confirmed().cancel(START)).isInstanceOf(InvalidStateException.class);
    }

    @Test
    void noShowFromTheStartOnwardReleasesTheCar() {
      Reservation noShow = confirmed().markNoShow(START.plus(Duration.ofHours(5)));

      assertThat(noShow.state()).isEqualTo(ReservationState.NO_SHOW);
      assertThat(noShow.pendingAction()).contains(InventoryAction.RELEASE);
      assertThatThrownBy(() -> confirmed().markNoShow(START.minusSeconds(1)))
          .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void pickUpThenReturnRegistersTheReturnWithTheInventory() {
      Reservation pickedUp = confirmed().pickUp();
      Reservation returned = pickedUp.registerReturn();

      assertThat(pickedUp.state()).isEqualTo(ReservationState.PICKED_UP);
      assertThat(pickedUp.pendingAction()).isEmpty();
      assertThat(returned.state()).isEqualTo(ReservationState.RETURNED);
      assertThat(returned.pendingAction()).contains(InventoryAction.RETURN);
      assertThat(returned.inventoryUpdated().pendingAction()).isEmpty();
    }

    @Test
    void rejectsChangesThatTheStateDoesNotAllow() {
      Reservation cancelled = confirmed().cancel(NOW);

      assertThatThrownBy(cancelled::pickUp).isInstanceOf(InvalidStateException.class);
      assertThatThrownBy(() -> cancelled.cancel(NOW)).isInstanceOf(InvalidStateException.class);
      assertThatThrownBy(() -> confirmed().registerReturn())
          .isInstanceOf(InvalidStateException.class);
      assertThatThrownBy(() -> confirmed().pickUp().markNoShow(START))
          .isInstanceOf(InvalidStateException.class);
    }

    @Test
    void lostHoldCancelsTheReservation() {
      Reservation lost =
          Reservation.create(ID, "customer-1", rental(START, 3), PRICE, POLICY, NOW).holdLost();

      assertThat(lost.state()).isEqualTo(ReservationState.CANCELLED);
      assertThat(lost.pendingAction()).isEqualTo(Optional.empty());
    }
  }

  @Nested
  @DisplayName("Identifiers")
  class Identifiers {

    @Test
    void sameIdempotencyKeyGivesTheSameIdWithinACountry() {
      assertThat(ReservationId.forIdempotencyKey("pl", "c-1", "k"))
          .isEqualTo(ReservationId.forIdempotencyKey("pl", "c-1", "k"))
          .isNotEqualTo(ReservationId.forIdempotencyKey("de", "c-1", "k"))
          .isNotEqualTo(ReservationId.forIdempotencyKey("pl", "c-2", "k"))
          .isNotEqualTo(ReservationId.forIdempotencyKey("pl", "c-1", "other"));
      assertThat(ReservationId.forIdempotencyKey("pl", "a:b", "c"))
          .as("the customer ID cannot run into the key")
          .isNotEqualTo(ReservationId.forIdempotencyKey("pl", "a", "b:c"));
      assertThat(ID.toString()).hasSize(36);
    }
  }

  @Nested
  @DisplayName("Price and choices")
  class PriceAndChoices {

    @Test
    void keepsThePriceItWasMadeWithThroughEveryChange() {
      Reservation cancelled = confirmed().cancel(NOW);
      assertThat(cancelled.price()).contains(PRICE);
    }

    @Test
    void comparesTheTotalTheCustomerSaw() {
      assertThat(PRICE.hasTotal("PLN", new BigDecimal("447"))).isTrue();
      assertThat(PRICE.hasTotal("PLN", new BigDecimal("446.99"))).isFalse();
      assertThat(PRICE.hasTotal("EUR", new BigDecimal("447.00"))).isFalse();
    }

    @Test
    void aPriceHasLinesAndIsNotNegative() {
      assertThatThrownBy(
              () -> new Price("PLN", List.of(), BigDecimal.ONE, Optional.empty(), BigDecimal.ONE))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() ->
              new Price(
                  "PLN", PRICE.lines(), BigDecimal.ONE, Optional.empty(), new BigDecimal("-1")))
          .isInstanceOf(InvalidReservationException.class);
    }

    @Test
    void aRentalHasBasicCoverUnlessChosenAndItsExtrasInOrder() {
      assertThat(rental(START, 3).insurance()).isEqualTo("BASIC");
      RentalRequest chosen =
          new RentalRequest(
              "waw-airport",
              "krk-airport",
              "SEDAN",
              "ECONOMY",
              START,
              3,
              "FULL",
              List.of(new ExtraChoice("ROAD_TOLL", 1), new ExtraChoice("CHILD_SEAT", 2)));
      assertThat(chosen.extras())
          .extracting(ExtraChoice::code)
          .containsExactly("CHILD_SEAT", "ROAD_TOLL");
      assertThatThrownBy(() ->
              new RentalRequest(
                  "waw-airport",
                  "waw-airport",
                  "SEDAN",
                  "ECONOMY",
                  START,
                  3,
                  "BASIC",
                  List.of(new ExtraChoice("GPS", 1), new ExtraChoice("GPS", 2))))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() ->
              new RentalRequest(
                  "waw-airport", "waw-airport", "SEDAN", "ECONOMY", START, 3, "gold", null))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() -> new ExtraChoice("GPS", 0))
          .isInstanceOf(InvalidReservationException.class);
      assertThatThrownBy(() -> new ExtraChoice("gps", 1))
          .isInstanceOf(InvalidReservationException.class);
    }
  }
}
