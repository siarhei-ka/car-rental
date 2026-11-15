package psn.car.rental.insurance.mock;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The pricing rules of the mock. They are configuration, not code.
 *
 * @param apiKey the key every caller sends in the header {@code X-Api-Key}
 * @param basePrices the price per day of each product, in units of the rental currency
 * @param youngDriverAge a driver younger than this pays the young driver factor
 * @param youngDriverFactor applied to a driver under {@code youngDriverAge}
 * @param foreignLicenceFactor applied when the licence country differs from the rental country
 * @param delay how long each answer waits, to try out the caller's time limit
 */
@ConfigurationProperties("insurance-mock")
public record MockProperties(
    String apiKey,
    Map<String, BigDecimal> basePrices,
    @DefaultValue("25") int youngDriverAge,
    @DefaultValue("1.5") BigDecimal youngDriverFactor,
    @DefaultValue("1.2") BigDecimal foreignLicenceFactor,
    @DefaultValue("PT0S") Duration delay) {

  public MockProperties {
    basePrices = basePrices == null ? Map.of() : Map.copyOf(basePrices);
  }
}
