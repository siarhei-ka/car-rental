package psn.car.rental.pricing.domain;

import java.util.regex.Pattern;

/** What a daily rate applies to: a location of the country, a car type and a car class. */
public record RateKey(String locationId, String carType, String carClass) {

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public RateKey {
    if (locationId == null || !ID.matcher(locationId).matches()) {
      throw new InvalidPriceException("A location ID has 1 to 64 letters, digits, '.', '_' or '-'");
    }
    if (carType == null || !CODE.matcher(carType).matches()) {
      throw new InvalidPriceException("A car type is an upper-case code such as SEDAN");
    }
    if (carClass == null || !CODE.matcher(carClass).matches()) {
      throw new InvalidPriceException("A car class is an upper-case code such as ECONOMY");
    }
  }
}
