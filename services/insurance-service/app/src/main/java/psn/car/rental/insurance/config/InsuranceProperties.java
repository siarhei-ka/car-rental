package psn.car.rental.insurance.config;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the insurance service.
 *
 * @param countries countries served from the start with their currency, in addition to those
 *     recorded and those announced by the administration service. Usually empty
 * @param administrationUrl where the list of countries is loaded from at startup, or null
 * @param providerUrl the insurance provider
 * @param providerApiKey the key the provider expects in the header {@code X-Api-Key}
 * @param providerTimeout the time limit of each call to the provider (NFR-9)
 */
@ConfigurationProperties("insurance")
public record InsuranceProperties(
    Map<String, String> countries,
    URI administrationUrl,
    URI providerUrl,
    String providerApiKey,
    @DefaultValue("PT2S") Duration providerTimeout) {

  public InsuranceProperties {
    countries = countries == null ? Map.of() : Map.copyOf(countries);
  }
}
