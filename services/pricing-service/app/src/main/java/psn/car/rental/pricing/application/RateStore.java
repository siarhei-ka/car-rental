package psn.car.rental.pricing.application;

import java.util.List;
import java.util.Optional;
import psn.car.rental.pricing.domain.DailyRate;
import psn.car.rental.pricing.tenant.Country;

/** Where the daily rates of each country are kept. */
public interface RateStore {

  /** The rates of a country, optionally only those of one location, ordered by their key. */
  List<DailyRate> list(Country country, Optional<String> locationId);

  /** Creates the rate, or replaces the existing rate of the same location, type and class. */
  void save(Country country, DailyRate rate);
}
