package psn.car.rental.search.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import psn.car.rental.search.SearchProperties;
import psn.car.rental.search.application.CarCatalog;
import psn.car.rental.search.application.DependencyUnavailableException;
import psn.car.rental.search.application.Inventory;
import psn.car.rental.search.application.Locations;
import psn.car.rental.search.application.PriceList;
import psn.car.rental.search.domain.Availability;
import psn.car.rental.search.domain.CarKind;
import psn.car.rental.search.domain.DailyRate;
import psn.car.rental.search.domain.Location;
import psn.car.rental.search.domain.Price;

/** HTTP clients of the services the search depends on, each with the time limit of NFR-9. */
@Configuration(proxyBeanMethods = false)
class HttpClients {

  private static final ParameterizedTypeReference<List<CarKind>> KINDS =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<Availability>> AVAILABILITY =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<List<RateJson>> RATES =
      new ParameterizedTypeReference<>() {};

  @Bean
  Locations locations(RestClient.Builder builder, SearchProperties properties) {
    RestClient http = client(builder, properties.locationInfoUrl(), properties);
    return (country, id) ->
        call(
            "location info",
            () -> {
              try {
                return Optional.ofNullable(
                    http.get()
                        .uri("/api/v1/locations/{country}/{id}", country, id)
                        .retrieve()
                        .body(Location.class));
              } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                  return Optional.empty();
                }
                throw e;
              }
            });
  }

  @Bean
  CarCatalog carCatalog(RestClient.Builder builder, SearchProperties properties) {
    RestClient http = client(builder, properties.carTypeInfoUrl(), properties);
    return new CarCatalog() {
      @Override
      public List<CarKind> carTypes() {
        return call(
            "car type info", () -> http.get().uri("/api/v1/car-types").retrieve().body(KINDS));
      }

      @Override
      public List<CarKind> carClasses() {
        return call(
            "car type info", () -> http.get().uri("/api/v1/car-classes").retrieve().body(KINDS));
      }
    };
  }

  @Bean
  Inventory inventory(RestClient.Builder builder, SearchProperties properties) {
    RestClient http = client(builder, properties.inventoryUrl(), properties);
    return query ->
        call(
            "inventory",
            () ->
                http.get()
                    .uri(uri ->
                        uri.path("/api/v1/availability")
                            .queryParam("locationId", query.pickUpLocationId())
                            .queryParam("returnLocationId", query.returnLocationId())
                            .queryParam("start", query.start().toString())
                            .queryParam("days", query.days())
                            .build())
                    .header("X-Country", query.country())
                    .retrieve()
                    .body(AVAILABILITY));
  }

  @Bean
  PriceList priceList(RestClient.Builder builder, SearchProperties properties) {
    RestClient http = client(builder, properties.pricingUrl(), properties);
    return (country, locationId) ->
        call(
            "pricing",
            () ->
                http
                    .get()
                    .uri(uri ->
                        uri.path("/api/v1/rates").queryParam("locationId", locationId).build())
                    .header("X-Country", country)
                    .retrieve()
                    .body(RATES)
                    .stream()
                    .map(RateJson::toDailyRate)
                    .toList());
  }

  /** A rate as the pricing service sends it. */
  record RateJson(String carType, String carClass, Price dailyRate) {

    DailyRate toDailyRate() {
      return new DailyRate(carType, carClass, dailyRate);
    }
  }

  private static RestClient client(
      RestClient.Builder builder, URI baseUrl, SearchProperties properties) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
    factory.setReadTimeout(properties.timeout());
    return builder.clone().baseUrl(baseUrl.toString()).requestFactory(factory).build();
  }

  static <T> T call(String service, Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientException e) {
      throw new DependencyUnavailableException("The " + service + " service is not available", e);
    }
  }
}
