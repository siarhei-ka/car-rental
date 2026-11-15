package psn.car.rental.ancillary.config;

import java.net.URI;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the ancillary service.
 *
 * @param countries countries served from the start with their currency, in addition to those that
 *     already have a schema and those announced by the administration service. Usually empty
 * @param seedData whether example data is loaded when a country's schema is created
 * @param administrationUrl where the list of countries is loaded from at startup, or null
 */
@ConfigurationProperties("ancillary")
public record AncillaryProperties(
    Map<String, String> countries, @DefaultValue("false") boolean seedData, URI administrationUrl) {

  public AncillaryProperties {
    countries = countries == null ? Map.of() : Map.copyOf(countries);
  }
}
