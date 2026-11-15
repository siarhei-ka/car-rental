package psn.car.rental.reservation.inventory;

import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import psn.car.rental.reservation.application.Inventory;
import psn.car.rental.reservation.config.ReservationProperties;

/** The HTTP client of the inventory service, with the time limit of NFR-9. */
@Configuration(proxyBeanMethods = false)
class InventoryClientConfig {

  @Bean
  Inventory inventory(RestClient.Builder builder, ReservationProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.inventoryTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.inventoryTimeout());
    RestClient http =
        builder
            .baseUrl(properties.inventoryUrl().toString())
            .requestFactory(requestFactory)
            .build();
    return new InventoryClient(http);
  }
}
