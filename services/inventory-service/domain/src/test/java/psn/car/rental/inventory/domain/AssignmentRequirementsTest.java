package psn.car.rental.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static psn.car.rental.inventory.domain.Fixtures.AIRPORT;
import static psn.car.rental.inventory.domain.Fixtures.NOW;
import static psn.car.rental.inventory.domain.Fixtures.day;
import static psn.car.rental.inventory.domain.Fixtures.hold;
import static psn.car.rental.inventory.domain.Fixtures.ledger;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Proves the requirements of the original assignment (NFR-24).
 *
 * <ol>
 *   <li>The system should allow reservation of a car of a given type at a desired date and time for
 *       a given number of days.
 *   <li>There are 3 types of cars (Sedan, SUV and van).
 *   <li>The number of cars of each type is limited.
 * </ol>
 */
@DisplayName("Assignment requirements")
class AssignmentRequirementsTest {

  @Nested
  @DisplayName("1. Reserve a car of a given type at a date and time for a number of days")
  class ReserveCar {

    @Test
    void reservesCarOfGivenTypeAtDesiredDateAndTimeForGivenNumberOfDays() {
      StockLedger sedans = ledger("SEDAN", 1);
      Instant desiredStart = Instant.parse("2026-12-24T09:30:00Z");

      Hold reserved =
          sedans.placeHold(hold("r-1"), new RentalPeriod(desiredStart, 3), AIRPORT, NOW);

      assertThat(sedans.key().carType()).isEqualTo(new CarTypeCode("SEDAN"));
      assertThat(reserved.period().start()).isEqualTo(desiredStart);
      assertThat(reserved.period().days()).isEqualTo(3);
      assertThat(reserved.period().end()).isEqualTo(Instant.parse("2026-12-27T09:30:00Z"));
    }

    @Test
    void carIsReservedForTheWholePeriodAndNotLonger() {
      StockLedger sedans = ledger("SEDAN", 1);
      sedans.placeHold(hold("r-1"), new RentalPeriod(day(10), 3), AIRPORT, NOW);

      assertThat(sedans.availableCars(new RentalPeriod(day(10), 1), AIRPORT)).isZero();
      assertThat(sedans.availableCars(new RentalPeriod(day(12), 1), AIRPORT)).isZero();
      assertThat(sedans.availableCars(new RentalPeriod(day(13), 1), AIRPORT)).isEqualTo(1);
      assertThat(sedans.availableCars(new RentalPeriod(day(9), 1), AIRPORT)).isEqualTo(1);
    }

    @Test
    void carCanBeReservedAgainAtTheMomentThePreviousRentalEnds() {
      StockLedger sedans = ledger("SEDAN", 1);
      RentalPeriod first = new RentalPeriod(day(10), 2);
      sedans.placeHold(hold("r-1"), first, AIRPORT, NOW);

      Hold next = sedans.placeHold(hold("r-2"), new RentalPeriod(first.end(), 1), AIRPORT, NOW);

      assertThat(next.period().start()).isEqualTo(first.end());
    }

    @Test
    void rentalMustLastAtLeastOneDay() {
      assertThatThrownBy(() -> new RentalPeriod(day(10), 0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("2. There are 3 types of cars: Sedan, SUV and van")
  class ThreeCarTypes {

    @Test
    void eachOfTheThreeTypesCanBeReserved() {
      for (String type : new String[] {"SEDAN", "SUV", "VAN"}) {
        StockLedger cars = ledger(type, 1);

        Hold reserved =
            cars.placeHold(hold("r-" + type), new RentalPeriod(day(10), 2), AIRPORT, NOW);

        assertThat(reserved.status()).isEqualTo(HoldStatus.PROVISIONAL);
        assertThat(cars.key().carType().value()).isEqualTo(type);
      }
    }

    @Test
    void carsOfOneTypeAreNotUsedForAnotherType() {
      StockLedger sedans = ledger("SEDAN", 1);
      StockLedger suvs = ledger("SUV", 1);
      RentalPeriod period = new RentalPeriod(day(10), 2);

      sedans.placeHold(hold("r-1"), period, AIRPORT, NOW);

      assertThat(sedans.availableCars(period, AIRPORT)).isZero();
      assertThat(suvs.availableCars(period, AIRPORT)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("3. The number of cars of each type is limited")
  class LimitedNumberOfCars {

    @Test
    void acceptsReservationsUpToTheNumberOfCars() {
      StockLedger vans = ledger("VAN", 2);
      RentalPeriod period = new RentalPeriod(day(10), 2);

      vans.placeHold(hold("r-1"), period, AIRPORT, NOW);
      vans.placeHold(hold("r-2"), period, AIRPORT, NOW);

      assertThat(vans.holds()).hasSize(2);
      assertThat(vans.availableCars(period, AIRPORT)).isZero();
    }

    @Test
    void rejectsReservationWhenAllCarsOfTheTypeAreTaken() {
      StockLedger vans = ledger("VAN", 2);
      RentalPeriod period = new RentalPeriod(day(10), 2);
      vans.placeHold(hold("r-1"), period, AIRPORT, NOW);
      vans.placeHold(hold("r-2"), period, AIRPORT, NOW);

      assertThatThrownBy(() -> vans.placeHold(hold("r-3"), period, AIRPORT, NOW))
          .isInstanceOf(NoCarAvailableException.class);
      assertThat(vans.holds()).hasSize(2);
    }

    @Test
    void rejectsReservationThatOverlapsOnlyPartly() {
      StockLedger suvs = ledger("SUV", 1);
      suvs.placeHold(hold("r-1"), new RentalPeriod(day(10), 3), AIRPORT, NOW);

      assertThatThrownBy(
              () -> suvs.placeHold(hold("r-2"), new RentalPeriod(day(12), 3), AIRPORT, NOW))
          .isInstanceOf(NoCarAvailableException.class);
      assertThatThrownBy(
              () -> suvs.placeHold(hold("r-3"), new RentalPeriod(day(8), 3), AIRPORT, NOW))
          .isInstanceOf(NoCarAvailableException.class);
    }

    @Test
    void rejectsReservationWhenThereAreNoCarsOfTheType() {
      StockLedger vans = ledger("VAN", 0);

      assertThatThrownBy(
              () -> vans.placeHold(hold("r-1"), new RentalPeriod(day(10), 1), AIRPORT, NOW))
          .isInstanceOf(NoCarAvailableException.class);
    }

    @Test
    void countsOnlyReservationsThatOverlapAtTheSameMoment() {
      StockLedger sedans = ledger("SEDAN", 2);
      sedans.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), AIRPORT, NOW);
      sedans.placeHold(hold("r-2"), new RentalPeriod(day(12), 2), AIRPORT, NOW);

      Hold spanning = sedans.placeHold(hold("r-3"), new RentalPeriod(day(10), 4), AIRPORT, NOW);

      assertThat(spanning.id()).isEqualTo(hold("r-3"));
      assertThat(sedans.availableCars(new RentalPeriod(day(10), 4), AIRPORT)).isZero();
    }
  }
}
