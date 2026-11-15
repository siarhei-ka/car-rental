package psn.car.rental.pricing.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psn.car.rental.pricing.domain.DailyRate;
import psn.car.rental.pricing.domain.Money;
import psn.car.rental.pricing.domain.RateKey;
import psn.car.rental.pricing.tenant.Countries;
import psn.car.rental.pricing.tenant.Country;
import psn.car.rental.shared.events.Analytics;

/** Reads and changes daily rates. A rate is always in the currency of its country. */
@Service
public class RateService {

  private final RateStore store;
  private final Countries countries;
  private final Analytics analytics;

  public RateService(RateStore store, Countries countries, Analytics analytics) {
    this.store = store;
    this.countries = countries;
    this.analytics = analytics;
  }

  public List<DailyRate> list(Country country, Optional<String> locationId) {
    return store.list(country, locationId);
  }

  /** Sets a rate, and records the administrator's action in the same transaction. */
  @Transactional
  public DailyRate save(Country country, RateKey key, String amount) {
    DailyRate rate = new DailyRate(key, Money.of(amount, countries.currencyOf(country)));
    store.save(country, rate);
    analytics.record(
        "admin.rate-changed",
        country.code(),
        Map.of(
            "locationId", key.locationId(),
            "carType", key.carType(),
            "carClass", key.carClass(),
            "dailyRate", money(rate.price())));
    return rate;
  }

  static Map<String, Object> money(Money money) {
    return Map.of(
        "amount", money.amount().toPlainString(), "currency", money.currency().getCurrencyCode());
  }
}
