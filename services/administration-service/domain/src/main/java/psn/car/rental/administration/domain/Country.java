package psn.car.rental.administration.domain;

import java.util.Currency;

/**
 * A country is a tenant. All prices of a reservation are in the currency of its country.
 *
 * @param code two lowercase letters, for example {@code pl}
 * @param currency an ISO 4217 code, for example {@code PLN}
 */
public record Country(String code, String name, String currency, boolean active) {

  public Country {
    Rules.require(Rules.COUNTRY_CODE, code, "Country code");
    name = Rules.requireName(name, "Country name");
    if (currency == null) {
      throw new InvalidReferenceDataException("Currency is required");
    }
    try {
      Currency.getInstance(currency);
    } catch (IllegalArgumentException e) {
      throw new InvalidReferenceDataException("Currency is not an ISO 4217 code: " + currency);
    }
  }
}
