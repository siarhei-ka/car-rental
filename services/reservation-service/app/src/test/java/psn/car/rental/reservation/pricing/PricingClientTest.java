package psn.car.rental.reservation.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import psn.car.rental.reservation.application.PricingRefusedException;
import psn.car.rental.reservation.application.PricingUnavailableException;
import psn.car.rental.reservation.domain.ExtraChoice;
import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.tenant.Country;

@DisplayName("Pricing client")
class PricingClientTest {

  private static final String QUOTES = "http://pricing/api/v1/customers/customer-1/quotes";
  private static final RentalRequest RENTAL =
      new RentalRequest(
          "waw-airport",
          "krk-airport",
          "SEDAN",
          "ECONOMY",
          Instant.parse("2026-12-10T10:00:00Z"),
          3,
          "FULL",
          List.of(new ExtraChoice("CHILD_SEAT", 2)));

  private MockRestServiceServer server;
  private PricingClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://pricing");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new PricingClient(builder.build());
  }

  @Test
  void asksThePriceOfTheRentalForTheCustomer() {
    server
        .expect(requestTo(QUOTES))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Country", "pl"))
        .andExpect(jsonPath("$.pickUpLocationId").value("waw-airport"))
        .andExpect(jsonPath("$.returnLocationId").value("krk-airport"))
        .andExpect(jsonPath("$.start").value("2026-12-10T10:00:00Z"))
        .andExpect(jsonPath("$.insurance").value("FULL"))
        .andExpect(jsonPath("$.extras[0].code").value("CHILD_SEAT"))
        .andExpect(jsonPath("$.extras[0].quantity").value(2))
        .andRespond(
            withSuccess(
                """
                {"currency":"PLN","lines":[{"kind":"CAR_RENTAL","code":"SEDAN/ECONOMY",
                  "name":"Car rental","quantity":1,"unitPrice":{"amount":"149.00","currency":"PLN"},
                  "charge":"PER_DAY","amount":{"amount":"447.00","currency":"PLN"}}],
                 "subtotal":{"amount":"447.00","currency":"PLN"},
                 "discount":{"name":"Week","amount":{"amount":"22.35","currency":"PLN"}},
                 "total":{"amount":"424.65","currency":"PLN"}}
                """,
                MediaType.APPLICATION_JSON));

    Price price = client.quote(new Country("pl"), "customer-1", RENTAL);

    assertThat(price.currency()).isEqualTo("PLN");
    assertThat(price.lines())
        .singleElement()
        .satisfies(l -> {
          assertThat(l.kind()).isEqualTo("CAR_RENTAL");
          assertThat(l.amount()).isEqualByComparingTo("447.00");
        });
    assertThat(price.discount())
        .hasValueSatisfying(d -> {
          assertThat(d.name()).isEqualTo("Week");
          assertThat(d.amount()).isEqualByComparingTo("22.35");
        });
    assertThat(price.hasTotal("PLN", new BigDecimal("424.65"))).isTrue();
    server.verify();
  }

  @Test
  void passesARefusalOn() {
    server
        .expect(requestTo(QUOTES))
        .andRespond(
            withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(
                    """
                    {"type":"/problems/driver-too-young","title":"The rental cannot be priced",
                     "status":422,"detail":"The driver must be at least 21 years old"}
                    """));

    assertThatThrownBy(() -> client.quote(new Country("pl"), "customer-1", RENTAL))
        .isInstanceOfSatisfying(
            PricingRefusedException.class,
            e -> {
              assertThat(e.status()).isEqualTo(422);
              assertThat(e.type()).isEqualTo("driver-too-young");
              assertThat(e.title()).isEqualTo("The rental cannot be priced");
              assertThat(e.getMessage()).isEqualTo("The driver must be at least 21 years old");
            });
  }

  @Test
  void reportsAFailureOrATimeoutAsUnavailable() {
    server
        .expect(requestTo(QUOTES))
        .andRespond(
            withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"detail\":\"the insurance service is not available\"}"));
    assertThatThrownBy(() -> client.quote(new Country("pl"), "customer-1", RENTAL))
        .isInstanceOf(PricingUnavailableException.class)
        .hasMessageContaining("insurance");

    server.reset();
    server
        .expect(requestTo(QUOTES))
        .andRespond(withException(new SocketTimeoutException("timed out")));
    assertThatThrownBy(() -> client.quote(new Country("pl"), "customer-1", RENTAL))
        .isInstanceOf(PricingUnavailableException.class)
        .hasMessageContaining("pricing service is not available");
  }

  @Test
  void aRefusalWithoutProblemDetailsStillExplainsItself() throws IOException {
    server.expect(requestTo(QUOTES)).andRespond(withStatus(HttpStatus.BAD_REQUEST));
    assertThatThrownBy(() -> client.quote(new Country("pl"), "customer-1", RENTAL))
        .isInstanceOfSatisfying(
            PricingRefusedException.class,
            e -> {
              assertThat(e.status()).isEqualTo(400);
              assertThat(e.getMessage()).isEqualTo("The rental cannot be priced");
            });
  }
}
