package psn.car.rental.insurance.domain;

import java.util.regex.Pattern;

/**
 * What the insurance provider learns about the driver: the age on the start of the rental and the
 * country that issued the driving licence. The name and the date of birth are never sent.
 */
public record Driver(int age, String licenceCountry) {

  private static final Pattern COUNTRY = Pattern.compile("[a-z]{2}");

  public Driver {
    if (age < 18 || age > 120) {
      throw new InvalidInsuranceException("The age of a driver is from 18 to 120");
    }
    if (licenceCountry == null || !COUNTRY.matcher(licenceCountry).matches()) {
      throw new InvalidInsuranceException(
          "The licence country is a lower-case ISO 3166-1 alpha-2 code");
    }
  }
}
