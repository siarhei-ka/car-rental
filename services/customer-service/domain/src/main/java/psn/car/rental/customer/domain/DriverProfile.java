package psn.car.rental.customer.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;

/**
 * What the system knows about a customer as a driver: the date of birth and the country that issued
 * the driving licence. The age is never stored; it is calculated for a given day, such as the start
 * of a rental.
 */
public record DriverProfile(
    CustomerId customer, LocalDate dateOfBirth, LicenceCountry licenceCountry) {

  /** A driver is an adult; whether the driver is old enough to rent is checked per rental. */
  public static final int MINIMUM_AGE = 18;

  /** Older dates are typing errors. */
  public static final int MAXIMUM_AGE = 120;

  public DriverProfile {
    Objects.requireNonNull(customer, "customer");
    Objects.requireNonNull(licenceCountry, "licenceCountry");
    if (dateOfBirth == null) {
      throw new InvalidProfileException("The date of birth is required");
    }
  }

  /**
   * A profile entered on {@code today}.
   *
   * @throws InvalidProfileException if the driver is younger than {@value #MINIMUM_AGE} or older
   *     than {@value #MAXIMUM_AGE} years on that day
   */
  public static DriverProfile enter(
      CustomerId customer, LocalDate dateOfBirth, LicenceCountry licenceCountry, LocalDate today) {
    DriverProfile profile = new DriverProfile(customer, dateOfBirth, licenceCountry);
    if (dateOfBirth.isAfter(today)) {
      throw new InvalidProfileException("The date of birth is in the future");
    }
    int age = profile.ageOn(today);
    if (age < MINIMUM_AGE) {
      throw new InvalidProfileException("A driver is at least " + MINIMUM_AGE + " years old");
    }
    if (age > MAXIMUM_AGE) {
      throw new InvalidProfileException("A driver is at most " + MAXIMUM_AGE + " years old");
    }
    return profile;
  }

  /** The age in full years on a day. A person born on 29 February has a birthday on 1 March. */
  public int ageOn(LocalDate day) {
    return Period.between(dateOfBirth, day).getYears();
  }
}
