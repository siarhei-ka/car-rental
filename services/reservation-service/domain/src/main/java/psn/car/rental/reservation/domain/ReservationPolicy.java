package psn.car.rental.reservation.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * The rules every new reservation must meet.
 *
 * <p>The driver's minimum age is checked once customers exist (build step 4). That the return
 * location is in the same country follows from the tenant: a request is always for one country.
 *
 * @param maxDays the longest rental, 30 days by default
 */
public record ReservationPolicy(int maxDays) {

  public ReservationPolicy {
    if (maxDays < 1) {
      throw new IllegalArgumentException("maxDays must be at least 1, but was " + maxDays);
    }
  }

  /**
   * Checks a request.
   *
   * @throws InvalidReservationException if a rule is broken
   */
  public void check(RentalRequest request, Instant now) {
    Objects.requireNonNull(request, "request");
    Objects.requireNonNull(now, "now");
    if (!request.start().isAfter(now)) {
      throw new InvalidReservationException("The start must be in the future");
    }
    if (request.days() < 1 || request.days() > maxDays) {
      throw new InvalidReservationException(
          "A rental lasts from 1 to " + maxDays + " days, but was " + request.days());
    }
  }
}
