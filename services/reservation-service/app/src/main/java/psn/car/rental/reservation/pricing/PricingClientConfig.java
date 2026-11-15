package psn.car.rental.reservation.pricing;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import psn.car.rental.reservation.application.Pricing;
import psn.car.rental.reservation.config.ReservationProperties;

/** The HTTP client of the pricing service, with its time limit. */
@Configuration(proxyBeanMethods = false)
class PricingClientConfig {

  @Bean
  Pricing pricing(RestClient.Builder builder, ReservationProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.pricingTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.pricingTimeout());
    RestClient http =
        builder
            .clone()
            .baseUrl(properties.pricingUrl().toString())
            .requestFactory(requestFactory)
            .build();
    return new PricingClient(http);
  }
}
