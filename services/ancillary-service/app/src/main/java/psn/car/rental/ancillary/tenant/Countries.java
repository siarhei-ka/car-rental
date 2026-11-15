package psn.car.rental.ancillary.tenant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import org.springframework.stereotype.Component;

/**
 * The countries this service serves, with their currency. The list grows when the administration
 * service announces a new country, so a country added there needs no change to this service.
 */
@Component
public class Countries {

  /** A served country: whether it is active, and the currency of its prices. */
  record Served(boolean active, String currency) {}

  private final Map<String, Served> served = new ConcurrentSkipListMap<>();

  public List<Country> all() {
    return served.keySet().stream().map(Country::new).toList();
  }

  public boolean isServed(Country country) {
    return served.containsKey(country.code());
  }

  /** Called by {@link TenantMigrations} once the country's schema exists. */
  void register(Country country, boolean active, String currency) {
    served.put(country.code(), new Served(active, currency));
  }

  /** The currency of the country's prices, an ISO 4217 code. */
  public String currencyOf(Country country) {
    Served info = served.get(country.code());
    if (info == null) {
      throw new UnknownCountryException("Country " + country.code() + " is not served");
    }
    return info.currency();
  }

  /**
   * Resolves the country of a request.
   *
   * @throws UnknownCountryException if the code is missing or not served
   */
  public Country require(String code) {
    if (code == null || code.isBlank()) {
      throw new UnknownCountryException("The X-Country header is required");
    }
    String normalized = code.trim().toLowerCase(Locale.ROOT);
    if (!served.containsKey(normalized)) {
      throw new UnknownCountryException("Country " + code + " is not served");
    }
    return new Country(normalized);
  }
}
