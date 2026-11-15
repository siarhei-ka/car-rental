package psn.car.rental.insurance.provider;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import psn.car.rental.insurance.application.InsuranceProvider;
import psn.car.rental.insurance.application.ProviderUnavailableException;
import psn.car.rental.insurance.config.InsuranceProperties;
import psn.car.rental.insurance.domain.Driver;
import psn.car.rental.insurance.domain.Money;

/**
 * The insurance provider over HTTP, with its own API key and a time limit.
 *
 * <p>The client is built from a plain {@link RestClient} builder rather than the one of Spring
 * Boot, because the Boot builder adds the service's own access token, which must never reach a
 * third party.
 */
@Component
class HttpInsuranceProvider implements InsuranceProvider {

  private static final Logger log = LoggerFactory.getLogger(HttpInsuranceProvider.class);

  private final RestClient http;

  HttpInsuranceProvider(InsuranceProperties properties) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.providerTimeout()).build());
    factory.setReadTimeout(properties.providerTimeout());
    this.http =
        RestClient.builder()
            .baseUrl(String.valueOf(properties.providerUrl()))
            .requestFactory(factory)
            .defaultHeader("X-Api-Key", String.valueOf(properties.providerApiKey()))
            .build();
  }

  @Override
  public Money pricePerDay(
      String option, String rentalCountry, String currency, Optional<Driver> driver) {
    QuoteRequest request =
        new QuoteRequest(
            option,
            rentalCountry,
            currency,
            driver.map(Driver::age).orElse(null),
            driver.map(Driver::licenceCountry).orElse(null));
    QuoteAnswer answer;
    try {
      answer =
          http.post()
              .uri("/v1/quotes")
              .contentType(MediaType.APPLICATION_JSON)
              .body(request)
              .retrieve()
              .body(QuoteAnswer.class);
    } catch (RestClientException e) {
      log.warn("The insurance provider gave no price: {}", e.getMessage());
      throw new ProviderUnavailableException("The insurance provider is not available", e);
    }
    if (answer == null || !currency.equals(answer.currency())) {
      throw new ProviderUnavailableException(
          "The insurance provider answered in another currency", null);
    }
    return Money.of(answer.pricePerDay(), answer.currency());
  }

  record QuoteRequest(
      String product,
      String rentalCountry,
      String currency,
      Integer driverAge,
      String licenceCountry) {}

  record QuoteAnswer(String product, String pricePerDay, String currency, List<String> factors) {}
}
