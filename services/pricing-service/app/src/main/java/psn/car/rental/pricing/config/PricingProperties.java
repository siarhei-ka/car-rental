package psn.car.rental.pricing.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the pricing service.
 *
 * @param countries countries served from the start with their currency, in addition to those that
 *     already have a schema and those announced by the administration service. Usually empty
 * @param seedData whether example data is loaded when a country's schema is created
 * @param administrationUrl where the list of countries is loaded from at startup, or null
 * @param locationInfoUrl the location info service, for the time zone and country of a location
 * @param customerUrl the customer service, for the driver profile
 * @param insuranceUrl the insurance service, for the insurance price for the driver
 * @param ancillaryUrl the ancillary service, for extras and the one-way fee
 * @param discountUrl the discount service, for the discount of a quote
 * @param timeout the time limit of each call to another service (NFR-9)
 * @param maxDays the longest rental, the same limit as a reservation
 * @param minimumDriverAge the youngest a driver may be on the start of the rental
 */
@ConfigurationProperties("pricing")
public record PricingProperties(
    Map<String, String> countries,
    @DefaultValue("false") boolean seedData,
    URI administrationUrl,
    URI locationInfoUrl,
    URI customerUrl,
    URI insuranceUrl,
    URI ancillaryUrl,
    URI discountUrl,
    @DefaultValue("PT2S") Duration timeout,
    @DefaultValue("30") int maxDays,
    @DefaultValue("21") int minimumDriverAge) {

  public PricingProperties {
    countries = countries == null ? Map.of() : Map.copyOf(countries);
  }
}
