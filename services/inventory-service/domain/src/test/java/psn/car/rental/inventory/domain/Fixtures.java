package psn.car.rental.inventory.domain;

import java.time.Duration;
import java.time.Instant;

/** Shared test data. */
final class Fixtures {

  static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");
  static final LocationId AIRPORT = new LocationId("airport");
  static final LocationId DOWNTOWN = new LocationId("downtown");
  static final CarClassCode ECONOMY = new CarClassCode("ECONOMY");

  private Fixtures() {}

  /** A moment a number of days after {@link #NOW}. */
  static Instant day(int daysFromNow) {
    return NOW.plus(Duration.ofDays(daysFromNow));
  }

  /** A ledger of economy cars of a type at the airport. */
  static StockLedger ledger(String carType, int stock) {
    return new StockLedger(new StockKey(AIRPORT, new CarTypeCode(carType), ECONOMY), stock);
  }

  static HoldId hold(String id) {
    return new HoldId(id);
  }

  static TransferId transfer(String id) {
    return new TransferId(id);
  }
}
