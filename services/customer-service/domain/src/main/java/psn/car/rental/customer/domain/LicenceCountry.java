package psn.car.rental.customer.domain;

import java.util.Locale;
import java.util.Set;

/**
 * The country that issued the driving licence, as an ISO 3166-1 alpha-2 code in lower case, the way
 * the system writes country codes. Any country may issue a licence, not only the countries where
 * cars are rented.
 */
public record LicenceCountry(String code) {

  private static final Set<String> ISO_CODES =
      Locale.getISOCountries(Locale.IsoCountryCode.PART1_ALPHA2);

  public LicenceCountry {
    if (code == null || !ISO_CODES.contains(code.strip().toUpperCase(Locale.ROOT))) {
      throw new InvalidProfileException("The licence country must be an ISO 3166-1 alpha-2 code");
    }
    code = code.strip().toLowerCase(Locale.ROOT);
  }
}
