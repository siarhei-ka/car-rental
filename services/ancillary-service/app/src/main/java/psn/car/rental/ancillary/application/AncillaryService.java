package psn.car.rental.ancillary.application;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psn.car.rental.ancillary.domain.Charge;
import psn.car.rental.ancillary.domain.Extra;
import psn.car.rental.ancillary.domain.ExtraKind;
import psn.car.rental.ancillary.domain.InvalidPriceException;
import psn.car.rental.ancillary.domain.Money;
import psn.car.rental.ancillary.tenant.Countries;
import psn.car.rental.ancillary.tenant.Country;
import psn.car.rental.shared.events.Analytics;

/** Reads and changes extras and the one-way fee. Prices are in the currency of the country. */
@Service
public class AncillaryService {

  private final AncillaryStore store;
  private final Countries countries;
  private final Analytics analytics;

  public AncillaryService(AncillaryStore store, Countries countries, Analytics analytics) {
    this.store = store;
    this.countries = countries;
    this.analytics = analytics;
  }

  public List<Extra> extras(Country country, boolean activeOnly) {
    return store.extras(country, activeOnly);
  }

  /** Creates or changes an extra, and records the administrator's action in one transaction. */
  @Transactional
  public Extra saveExtra(Country country, ExtraChange change) {
    Extra extra =
        new Extra(
            change.code(),
            change.name(),
            change.kind(),
            change.charge(),
            Money.of(change.price(), countries.currencyOf(country)),
            change.maxQuantity(),
            change.active());
    store.save(country, extra);
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("code", extra.code());
    data.put("name", extra.name());
    data.put("kind", extra.kind().name());
    data.put("charge", extra.charge().name());
    data.put("price", money(extra.price()));
    data.put("maxQuantity", extra.maxQuantity());
    data.put("active", extra.active());
    analytics.record("admin.extra-changed", country.code(), data);
    return extra;
  }

  public Money oneWayFee(Country country) {
    return store.oneWayFee(country).orElseThrow(OneWayFeeNotSetException::new);
  }

  @Transactional
  public Money saveOneWayFee(Country country, String amount) {
    Money fee = Money.of(amount, countries.currencyOf(country));
    if (fee.isNegative()) {
      throw new InvalidPriceException("The one-way fee is not negative");
    }
    store.saveOneWayFee(country, fee);
    analytics.record("admin.one-way-fee-changed", country.code(), Map.of("fee", money(fee)));
    return fee;
  }

  private static Map<String, Object> money(Money money) {
    return Map.of(
        "amount", money.amount().toPlainString(), "currency", money.currency().getCurrencyCode());
  }

  /** The new state of an extra, with its price as a decimal string in the country's currency. */
  public record ExtraChange(
      String code,
      String name,
      ExtraKind kind,
      Charge charge,
      String price,
      int maxQuantity,
      boolean active) {}
}
