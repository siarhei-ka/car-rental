package psn.car.rental.pricing.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Objects;

/**
 * The period of a rental and the rules every reservation follows, checked again for a quote so that
 * a customer never sees the price of what cannot be reserved.
 */
public record Rental(Instant start, int days) {

  public Rental {
    Objects.requireNonNull(start, "start");
  }

  /**
   * @throws InvalidQuoteException if the start is not in the future or the length is out of range
   */
  public void checkLimits(Instant now, int maxDays) {
    if (!start.isAfter(now)) {
      throw new InvalidQuoteException("The start must be in the future");
    }
    if (days < 1 || days > maxDays) {
      throw new InvalidQuoteException(
          "A rental lasts from 1 to " + maxDays + " days, but was " + days);
    }
  }

  /** The day the rental starts, in the time zone of the pick-up location. */
  public LocalDate startDay(ZoneId zone) {
    return start.atZone(zone).toLocalDate();
  }

  /**
   * The driver's age in full years on the start day. A person born on 29 February has a birthday on
   * 1 March.
   *
   * @throws QuoteRefusedException if the driver is younger than the minimum age on that day
   */
  public int driverAge(LocalDate dateOfBirth, ZoneId zone, int minimumAge) {
    int age = Period.between(dateOfBirth, startDay(zone)).getYears();
    if (age < minimumAge) {
      throw new QuoteRefusedException(
          QuoteRefusedException.Reason.DRIVER_TOO_YOUNG,
          "The driver must be at least " + minimumAge + " years old on the start of the rental");
    }
    return age;
  }
}
