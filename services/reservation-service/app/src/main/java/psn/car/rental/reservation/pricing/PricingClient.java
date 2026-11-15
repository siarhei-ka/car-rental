package psn.car.rental.reservation.pricing;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import psn.car.rental.reservation.application.Pricing;
import psn.car.rental.reservation.application.PricingRefusedException;
import psn.car.rental.reservation.application.PricingUnavailableException;
import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.tenant.Country;

/**
 * Asks the pricing service for the price of a rental for a customer, with the reservation service's
 * own token. A refusal of the pricing service is passed on unchanged.
 */
class PricingClient implements Pricing {

  private final RestClient http;

  PricingClient(RestClient http) {
    this.http = http;
  }

  @Override
  public Price quote(Country country, String customerId, RentalRequest rental) {
    QuoteJson quote;
    try {
      quote =
          http.post()
              .uri("/api/v1/customers/{customerId}/quotes", customerId)
              .header("X-Country", country.code())
              .body(
                  new QuoteRequestJson(
                      rental.pickUpLocationId(),
                      rental.returnLocationId(),
                      rental.start().toString(),
                      rental.days(),
                      rental.carType(),
                      rental.carClass(),
                      rental.insurance(),
                      rental.extras().stream()
                          .map(e -> new ExtraJson(e.code(), e.quantity()))
                          .toList()))
              .retrieve()
              .body(QuoteJson.class);
    } catch (HttpClientErrorException e) {
      throw refused(e);
    } catch (RestClientResponseException e) {
      throw new PricingUnavailableException(
          "The price cannot be calculated right now: " + detail(e, "the pricing service failed"),
          e);
    } catch (RestClientException e) {
      throw new PricingUnavailableException(
          "The price cannot be calculated right now: the pricing service is not available", e);
    }
    return quote.toPrice();
  }

  @SuppressWarnings("unchecked")
  private static PricingRefusedException refused(HttpClientErrorException e) {
    Map<String, Object> problem = problem(e);
    String type = String.valueOf(problem.getOrDefault("type", "/problems/invalid-reservation"));
    return new PricingRefusedException(
        e.getStatusCode().value(),
        type.substring(type.lastIndexOf('/') + 1),
        String.valueOf(problem.getOrDefault("title", "The rental cannot be priced")),
        detail(e, "The rental cannot be priced"));
  }

  private static String detail(RestClientResponseException e, String fallback) {
    return problem(e).get("detail") instanceof String text ? text : fallback;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> problem(RestClientResponseException e) {
    try {
      Map<String, Object> body = e.getResponseBodyAs(Map.class);
      return body == null ? Map.of() : body;
    } catch (RuntimeException ignored) {
      return Map.of();
    }
  }

  record QuoteRequestJson(
      String pickUpLocationId,
      String returnLocationId,
      String start,
      int days,
      String carType,
      String carClass,
      String insurance,
      List<ExtraJson> extras) {}

  record ExtraJson(String code, int quantity) {}

  record MoneyJson(BigDecimal amount, String currency) {}

  record LineJson(
      String kind,
      String code,
      String name,
      int quantity,
      MoneyJson unitPrice,
      String charge,
      MoneyJson amount) {}

  record DiscountJson(String name, MoneyJson amount) {}

  record QuoteJson(
      String currency,
      List<LineJson> lines,
      MoneyJson subtotal,
      DiscountJson discount,
      MoneyJson total) {

    Price toPrice() {
      return new Price(
          currency,
          lines.stream()
              .map(l ->
                  new Price.Line(
                      l.kind(),
                      l.code(),
                      l.name(),
                      l.quantity(),
                      l.unitPrice().amount(),
                      l.charge(),
                      l.amount().amount()))
              .toList(),
          subtotal.amount(),
          Optional.ofNullable(discount).map(d -> new Price.Discount(d.name(), d.amount().amount())),
          total.amount());
    }
  }
}
