package psn.car.rental.inventory.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A rental that starts at an exact date and time and lasts a number of days.
 *
 * <p>One day is 24 hours. The period is half-open, {@code [start, end)}, so a car can be rented
 * again at the moment it is returned.
 */
public record RentalPeriod(Instant start, int days) {

  public RentalPeriod {
    Objects.requireNonNull(start, "start");
    if (days < 1) {
      throw new IllegalArgumentException("A rental lasts at least 1 day, but was " + days);
    }
  }

  /** The first moment that is no longer part of the rental. */
  public Instant end() {
    return start.plus(Duration.ofDays(days));
  }
}
