package psn.car.rental.inventory.tenant;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import org.springframework.stereotype.Component;

/**
 * The countries this service serves. The list grows when the administration service announces a new
 * country, so a country added there needs no change to this service.
 */
@Component
public class Countries {

  /** Code of each served country, and whether it is active. Inactive countries are still served. */
  private final Map<String, Boolean> served = new ConcurrentSkipListMap<>();

  public List<Country> all() {
    return served.keySet().stream().map(Country::new).toList();
  }

  public boolean isServed(Country country) {
    return served.containsKey(country.code());
  }

  /** Called by {@link TenantMigrations} once the country's schema exists. */
  void register(Country country, boolean active) {
    served.put(country.code(), active);
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
