package psn.car.rental.insurance.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * The price per day of an insurance product: the base price of the product, multiplied by the young
 * driver factor and by the foreign licence factor when they apply. Without driver data only the
 * base price is given, which a rental company can show as a "from" price.
 */
@Component
class PriceRules {

  private final MockProperties properties;

  PriceRules(MockProperties properties) {
    this.properties = properties;
  }

  Quote quote(QuoteRequest request) {
    BigDecimal base = properties.basePrices().get(request.product().toUpperCase(Locale.ROOT));
    if (base == null) {
      throw new IllegalArgumentException("Unknown product " + request.product());
    }
    Currency currency = Currency.getInstance(request.currency());
    BigDecimal price = base;
    List<String> factors = new ArrayList<>();
    if (request.driverAge() != null && request.driverAge() < properties.youngDriverAge()) {
      price = price.multiply(properties.youngDriverFactor());
      factors.add("YOUNG_DRIVER");
    }
    if (request.licenceCountry() != null
        && !request.licenceCountry().equalsIgnoreCase(request.rentalCountry())) {
      price = price.multiply(properties.foreignLicenceFactor());
      factors.add("FOREIGN_LICENCE");
    }
    price = price.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_UP);
    return new Quote(
        request.product().toUpperCase(Locale.ROOT),
        price.toPlainString(),
        currency.getCurrencyCode(),
        factors);
  }

  /**
   * @param driverAge the age of the driver on the start of the rental, or null for the base price
   * @param licenceCountry the country that issued the licence, or null for the base price
   */
  record QuoteRequest(
      String product,
      String rentalCountry,
      String currency,
      Integer driverAge,
      String licenceCountry) {}

  record Quote(String product, String pricePerDay, String currency, List<String> factors) {}
}
