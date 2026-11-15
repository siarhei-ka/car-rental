package psn.car.rental.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static psn.car.rental.inventory.domain.Fixtures.AIRPORT;
import static psn.car.rental.inventory.domain.Fixtures.DOWNTOWN;
import static psn.car.rental.inventory.domain.Fixtures.NOW;
import static psn.car.rental.inventory.domain.Fixtures.day;
import static psn.car.rental.inventory.domain.Fixtures.hold;
import static psn.car.rental.inventory.domain.Fixtures.ledger;
import static psn.car.rental.inventory.domain.Fixtures.transfer;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Stock ledger")
class StockLedgerTest {

  @Nested
  @DisplayName("One-way rentals")
  class OneWayRentals {

    @Test
    void oneWayHoldTakesTheCarFromItsStartOnward() {
      StockLedger cars = ledger("SEDAN", 1);

      Hold oneWay = cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), DOWNTOWN, NOW);

      assertThat(oneWay.oneWay()).isTrue();
      assertThat(oneWay.usedUntil()).isEmpty();
      assertThat(cars.availableCars(new RentalPeriod(day(20), 1), AIRPORT)).isZero();
    }

    @Test
    void carCanStillBeRentedBeforeAOneWayRentalStarts() {
      StockLedger cars = ledger("SEDAN", 1);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), DOWNTOWN, NOW);

      Hold earlier = cars.placeHold(hold("r-2"), new RentalPeriod(day(5), 5), AIRPORT, NOW);

      assertThat(earlier.oneWay()).isFalse();
    }

    @Test
    void rejectsRentalThatIsStillRunningWhenAOneWayRentalStarts() {
      StockLedger cars = ledger("SEDAN", 1);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), DOWNTOWN, NOW);

      assertThatThrownBy(
              () -> cars.placeHold(hold("r-2"), new RentalPeriod(day(8), 3), AIRPORT, NOW))
          .isInstanceOf(NoCarAvailableException.class);
    }

    @Test
    void rejectsOneWayRentalWhenALaterRentalReliesOnTheCar() {
      StockLedger cars = ledger("SEDAN", 1);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(20), 2), AIRPORT, NOW);

      assertThatThrownBy(
              () -> cars.placeHold(hold("r-2"), new RentalPeriod(day(5), 2), DOWNTOWN, NOW))
          .isInstanceOf(NoCarAvailableException.class);
    }

    @Test
    void returningAOneWayRentalMovesTheCarToTheReturnLocation() {
      StockLedger airport = ledger("SEDAN", 2);
      airport.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), DOWNTOWN, NOW);
      airport.confirmHold(hold("r-1"));

      Optional<Arrival> arrival = airport.completeHold(hold("r-1"));

      assertThat(airport.stock()).isEqualTo(1);
      assertThat(airport.holds()).isEmpty();
      assertThat(arrival).contains(new Arrival(airport.key().at(DOWNTOWN), 1));
    }

    @Test
    void arrivalCountsOnlyAfterItIsRegistered() {
      StockLedger downtown =
          new StockLedger(new StockKey(DOWNTOWN, new CarTypeCode("SEDAN"), Fixtures.ECONOMY), 0);
      RentalPeriod period = new RentalPeriod(day(20), 1);
      assertThat(downtown.availableCars(period, DOWNTOWN)).isZero();

      downtown.receiveCars(1);

      assertThat(downtown.stock()).isEqualTo(1);
      assertThat(downtown.availableCars(period, DOWNTOWN)).isEqualTo(1);
    }

    @Test
    void returningASameLocationRentalKeepsTheStock() {
      StockLedger cars = ledger("SEDAN", 1);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 5), AIRPORT, NOW);

      Optional<Arrival> arrival = cars.completeHold(hold("r-1"));

      assertThat(arrival).isEmpty();
      assertThat(cars.stock()).isEqualTo(1);
      assertThat(cars.availableCars(new RentalPeriod(day(11), 1), AIRPORT)).isEqualTo(1);
    }

    @Test
    void rejectsArrivalOfLessThanOneCar() {
      StockLedger cars = ledger("SEDAN", 1);

      assertThatThrownBy(() -> cars.receiveCars(0)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Holds")
  class Holds {

    @Test
    void newHoldIsProvisionalUntilItIsConfirmed() {
      StockLedger cars = ledger("SUV", 1);
      Hold placed = cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 1), AIRPORT, NOW);

      Hold confirmed = cars.confirmHold(hold("r-1"));

      assertThat(placed.status()).isEqualTo(HoldStatus.PROVISIONAL);
      assertThat(placed.createdAt()).isEqualTo(NOW);
      assertThat(confirmed.status()).isEqualTo(HoldStatus.CONFIRMED);
      assertThat(cars.findHold(hold("r-1"))).contains(confirmed);
      assertThat(cars.confirmHold(hold("r-1"))).isEqualTo(confirmed);
    }

    @Test
    void repeatingTheSameRequestReturnsTheExistingHold() {
      StockLedger cars = ledger("SUV", 1);
      RentalPeriod period = new RentalPeriod(day(10), 1);
      Hold first = cars.placeHold(hold("r-1"), period, AIRPORT, NOW);

      Hold repeated = cars.placeHold(hold("r-1"), period, AIRPORT, NOW.plusSeconds(30));

      assertThat(repeated).isEqualTo(first);
      assertThat(cars.holds()).hasSize(1);
    }

    @Test
    void rejectsReusingAnIdForADifferentRequest() {
      StockLedger cars = ledger("SUV", 2);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 1), AIRPORT, NOW);

      assertThatThrownBy(
              () -> cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), AIRPORT, NOW))
          .isInstanceOf(ConflictingRequestException.class);
      assertThatThrownBy(
              () -> cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 1), DOWNTOWN, NOW))
          .isInstanceOf(ConflictingRequestException.class);
    }

    @Test
    void releasingAHoldFreesTheCar() {
      StockLedger cars = ledger("SUV", 1);
      RentalPeriod period = new RentalPeriod(day(10), 1);
      cars.placeHold(hold("r-1"), period, AIRPORT, NOW);

      boolean released = cars.releaseHold(hold("r-1"));

      assertThat(released).isTrue();
      assertThat(cars.availableCars(period, AIRPORT)).isEqualTo(1);
      assertThat(cars.releaseHold(hold("r-1"))).isFalse();
    }

    @Test
    void provisionalHoldExpiresWhenItIsNotConfirmedInTime() {
      StockLedger cars = ledger("SUV", 2);
      Duration limit = Duration.ofMinutes(2);
      cars.placeHold(hold("old"), new RentalPeriod(day(10), 1), AIRPORT, NOW);
      cars.placeHold(hold("confirmed"), new RentalPeriod(day(10), 1), AIRPORT, NOW);
      cars.confirmHold(hold("confirmed"));

      assertThat(cars.expireProvisionalHolds(NOW.plusSeconds(119), limit)).isEmpty();
      List<HoldId> expired = cars.expireProvisionalHolds(NOW.plusSeconds(120), limit);

      assertThat(expired).containsExactly(hold("old"));
      assertThat(cars.holds()).extracting(Hold::id).containsExactly(hold("confirmed"));
    }

    @Test
    void unknownHoldCannotBeConfirmedOrCompleted() {
      StockLedger cars = ledger("SUV", 1);

      assertThatThrownBy(() -> cars.confirmHold(hold("missing")))
          .isInstanceOf(HoldNotFoundException.class);
      assertThatThrownBy(() -> cars.completeHold(hold("missing")))
          .isInstanceOf(HoldNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Stock changes by an administrator")
  class StockChanges {

    @Test
    void stockCanBeRaisedAndLoweredWhileCarsAreFree() {
      StockLedger cars = ledger("VAN", 3);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 1), AIRPORT, NOW);

      cars.changeStock(1, NOW);

      assertThat(cars.stock()).isEqualTo(1);
    }

    @Test
    void rejectsLoweringStockBelowTheCarsInUse() {
      StockLedger cars = ledger("VAN", 2);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), AIRPORT, NOW);
      cars.placeHold(hold("r-2"), new RentalPeriod(day(11), 2), AIRPORT, NOW);

      assertThatThrownBy(() -> cars.changeStock(1, NOW)).isInstanceOf(StockInUseException.class);
      assertThat(cars.stock()).isEqualTo(2);
    }

    @Test
    void rentalsThatAreOverDoNotBlockAStockReduction() {
      StockLedger cars = ledger("VAN", 1);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(1), 2), AIRPORT, NOW);

      cars.changeStock(0, day(3));

      assertThat(cars.stock()).isZero();
    }

    @Test
    void rejectsNegativeStock() {
      assertThatThrownBy(() -> ledger("VAN", -1)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> ledger("VAN", 1).changeStock(-1, NOW))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("Transfers")
  class Transfers {

    @Test
    void transferTakesItsCarsFromTheDepartureOnward() {
      StockLedger cars = ledger("SEDAN", 3);

      Transfer sent = cars.dispatchTransfer(transfer("t-1"), 2, day(5), DOWNTOWN, day(6));

      assertThat(sent.cars()).isEqualTo(2);
      assertThat(cars.transfers()).containsExactly(sent);
      assertThat(cars.availableCars(new RentalPeriod(day(1), 2), AIRPORT)).isEqualTo(3);
      assertThat(cars.availableCars(new RentalPeriod(day(4), 2), AIRPORT)).isEqualTo(1);
      assertThat(cars.availableCars(new RentalPeriod(day(30), 1), AIRPORT)).isEqualTo(1);
    }

    @Test
    void rejectsTransferOfCarsThatReservationsRelyOn() {
      StockLedger cars = ledger("SEDAN", 2);
      cars.placeHold(hold("r-1"), new RentalPeriod(day(10), 2), AIRPORT, NOW);

      assertThatThrownBy(() -> cars.dispatchTransfer(transfer("t-1"), 2, day(5), DOWNTOWN, day(6)))
          .isInstanceOf(NoCarAvailableException.class);
      assertThat(cars.transfers()).isEmpty();
    }

    @Test
    void completingATransferMovesTheCarsToTheTarget() {
      StockLedger cars = ledger("SEDAN", 3);
      cars.dispatchTransfer(transfer("t-1"), 2, day(5), DOWNTOWN, day(6));

      Arrival arrival = cars.completeTransfer(transfer("t-1"));

      assertThat(cars.stock()).isEqualTo(1);
      assertThat(cars.transfers()).isEmpty();
      assertThat(arrival).isEqualTo(new Arrival(cars.key().at(DOWNTOWN), 2));
    }

    @Test
    void repeatingTheSameTransferReturnsTheExistingOne() {
      StockLedger cars = ledger("SEDAN", 3);
      Transfer first = cars.dispatchTransfer(transfer("t-1"), 2, day(5), DOWNTOWN, day(6));

      Transfer repeated = cars.dispatchTransfer(transfer("t-1"), 2, day(5), DOWNTOWN, day(6));

      assertThat(repeated).isEqualTo(first);
      assertThatThrownBy(() -> cars.dispatchTransfer(transfer("t-1"), 1, day(5), DOWNTOWN, day(6)))
          .isInstanceOf(ConflictingRequestException.class);
    }

    @Test
    void rejectsInvalidTransfers() {
      StockLedger cars = ledger("SEDAN", 3);

      assertThatThrownBy(() -> cars.dispatchTransfer(transfer("t-1"), 1, day(5), AIRPORT, day(6)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> cars.dispatchTransfer(transfer("t-2"), 0, day(5), DOWNTOWN, day(6)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> cars.dispatchTransfer(transfer("t-3"), 1, day(5), DOWNTOWN, day(5)))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> cars.completeTransfer(transfer("missing")))
          .isInstanceOf(TransferNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("Restoring from stored data")
  class Restoring {

    @Test
    void restoredLedgerEnforcesTheSameRule() {
      StockLedger original = ledger("SUV", 2);
      RentalPeriod period = new RentalPeriod(day(10), 2);
      original.placeHold(hold("r-1"), period, AIRPORT, NOW);
      original.dispatchTransfer(transfer("t-1"), 1, day(9), DOWNTOWN, day(10));

      StockLedger restored =
          StockLedger.restore(
              original.key(), original.stock(), original.holds(), original.transfers());

      assertThat(restored.holds()).isEqualTo(original.holds());
      assertThat(restored.transfers()).isEqualTo(original.transfers());
      assertThatThrownBy(() -> restored.placeHold(hold("r-2"), period, AIRPORT, NOW))
          .isInstanceOf(NoCarAvailableException.class);
    }
  }

  @Nested
  @DisplayName("Identifiers")
  class Identifiers {

    @Test
    void identifiersMustNotBeBlank() {
      assertThatThrownBy(() -> new LocationId(" ")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new CarTypeCode("")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new CarClassCode(null)).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new HoldId("")).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new TransferId("")).isInstanceOf(IllegalArgumentException.class);
    }
  }
}
