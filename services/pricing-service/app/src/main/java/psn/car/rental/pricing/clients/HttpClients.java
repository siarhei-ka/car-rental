package psn.car.rental.pricing.clients;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
import psn.car.rental.pricing.application.Dependencies.Ancillaries;
import psn.car.rental.pricing.application.Dependencies.Discounts;
import psn.car.rental.pricing.application.Dependencies.DriverProfile;
import psn.car.rental.pricing.application.Dependencies.DriverProfiles;
import psn.car.rental.pricing.application.Dependencies.InsurancePrice;
import psn.car.rental.pricing.application.Dependencies.InsurancePrices;
import psn.car.rental.pricing.application.Dependencies.Location;
import psn.car.rental.pricing.application.Dependencies.Locations;
import psn.car.rental.pricing.application.Dependencies.OfferedExtra;
import psn.car.rental.pricing.application.DependencyUnavailableException;
import psn.car.rental.pricing.config.PricingProperties;
import psn.car.rental.pricing.domain.Charge;
import psn.car.rental.pricing.domain.InvalidQuoteException;
import psn.car.rental.pricing.domain.Money;
import psn.car.rental.pricing.domain.QuoteDiscount;

/**
 * HTTP clients of the services a quote depends on. Each call carries the pricing service's own
 * token, added by the shared security configuration, and has the time limit of NFR-9.
 */
@Configuration(proxyBeanMethods = false)
class HttpClients {

  private static final ParameterizedTypeReference<List<ExtraJson>> EXTRAS =
      new ParameterizedTypeReference<>() {};

  @Bean
  Locations locations(RestClient.Builder builder, PricingProperties properties) {
    RestClient http = client(builder, properties.locationInfoUrl(), properties);
    return (country, id) ->
        call(
            "location info",
            () ->
                notFoundAsEmpty(() ->
                    http.get()
                        .uri("/api/v1/locations/{country}/{id}", country, id)
                        .retrieve()
                        .body(Location.class)));
  }

  @Bean
  DriverProfiles driverProfiles(RestClient.Builder builder, PricingProperties properties) {
    RestClient http = client(builder, properties.customerUrl(), properties);
    return customerId ->
        call(
            "customer",
            () ->
                notFoundAsEmpty(() ->
                    http.get()
                        .uri("/api/v1/profiles/{id}", customerId)
                        .retrieve()
                        .body(DriverProfile.class)));
  }

  @Bean
  InsurancePrices insurancePrices(RestClient.Builder builder, PricingProperties properties) {
    RestClient http = client(builder, properties.insuranceUrl(), properties);
    return (country, option, driverAge, licenceCountry) ->
        call(
            "insurance",
            () -> {
              try {
                OptionJson answer =
                    http.post()
                        .uri("/api/v1/insurance-quotes")
                        .header("X-Country", country)
                        .body(
                            Map.of(
                                "option",
                                option,
                                "driverAge",
                                driverAge,
                                "licenceCountry",
                                licenceCountry))
                        .retrieve()
                        .body(OptionJson.class);
                return new InsurancePrice(
                    answer.code(), answer.name(), answer.pricePerDay().toMoney());
              } catch (HttpClientErrorException.BadRequest e) {
                throw new InvalidQuoteException(detail(e, "The insurance option is not offered"));
              }
            });
  }

  @Bean
  Ancillaries ancillaries(RestClient.Builder builder, PricingProperties properties) {
    RestClient http = client(builder, properties.ancillaryUrl(), properties);
    return new Ancillaries() {
      @Override
      public List<OfferedExtra> extras(String country) {
        return call(
            "ancillary",
            () ->
                http
                    .get()
                    .uri("/api/v1/extras")
                    .header("X-Country", country)
                    .retrieve()
                    .body(EXTRAS)
                    .stream()
                    .map(ExtraJson::toExtra)
                    .toList());
      }

      @Override
      public Optional<Money> oneWayFee(String country) {
        return call(
            "ancillary",
            () ->
                notFoundAsEmpty(() ->
                        http.get()
                            .uri("/api/v1/one-way-fee")
                            .header("X-Country", country)
                            .retrieve()
                            .body(MoneyJson.class))
                    .map(MoneyJson::toMoney));
      }
    };
  }

  @Bean
  Discounts discounts(RestClient.Builder builder, PricingProperties properties) {
    RestClient http = client(builder, properties.discountUrl(), properties);
    return (country, day, days, total, extras) ->
        call(
            "discount",
            () -> {
              EvaluationJson answer =
                  http.post()
                      .uri("/api/v1/discount-evaluations")
                      .header("X-Country", country)
                      .body(
                          new BasketJson(
                              day,
                              days,
                              MoneyJson.of(total),
                              extras.stream()
                                  .map(l ->
                                      new BasketLine(
                                          l.code(), l.quantity(), MoneyJson.of(l.amount())))
                                  .toList()))
                      .retrieve()
                      .body(EvaluationJson.class);
              return Optional.ofNullable(answer.discount())
                  .map(d -> new QuoteDiscount(d.name(), d.amount().toMoney()));
            });
  }

  record MoneyJson(String amount, String currency) {

    static MoneyJson of(Money money) {
      return new MoneyJson(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }

    Money toMoney() {
      return Money.of(amount, currency);
    }
  }

  record OptionJson(String code, String name, MoneyJson pricePerDay) {}

  record ExtraJson(String code, String name, Charge charge, MoneyJson price, int maxQuantity) {

    OfferedExtra toExtra() {
      return new OfferedExtra(code, name, charge, price.toMoney(), maxQuantity);
    }
  }

  record BasketJson(LocalDate date, int days, MoneyJson total, List<BasketLine> extras) {}

  record BasketLine(String code, int quantity, MoneyJson price) {}

  record EvaluationJson(DiscountJson discount) {}

  record DiscountJson(String name, MoneyJson amount) {}

  private static RestClient client(
      RestClient.Builder builder, URI baseUrl, PricingProperties properties) {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(properties.timeout()).build());
    factory.setReadTimeout(properties.timeout());
    return builder.clone().baseUrl(baseUrl.toString()).requestFactory(factory).build();
  }

  private static <T> Optional<T> notFoundAsEmpty(Supplier<T> request) {
    try {
      return Optional.ofNullable(request.get());
    } catch (HttpClientErrorException e) {
      if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
        return Optional.empty();
      }
      throw e;
    }
  }

  @SuppressWarnings("unchecked")
  private static String detail(HttpClientErrorException e, String fallback) {
    try {
      Map<String, Object> problem = e.getResponseBodyAs(Map.class);
      return problem != null && problem.get("detail") instanceof String text ? text : fallback;
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  static <T> T call(String service, Supplier<T> request) {
    try {
      return request.get();
    } catch (RestClientException e) {
      throw new DependencyUnavailableException(
          "The price cannot be calculated right now: the " + service + " service is not available",
          e);
    }
  }
}
