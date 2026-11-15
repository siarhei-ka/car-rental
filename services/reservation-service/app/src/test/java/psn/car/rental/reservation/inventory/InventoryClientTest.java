package psn.car.rental.reservation.inventory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import psn.car.rental.reservation.application.ConflictingRequestException;
import psn.car.rental.reservation.application.HoldNotFoundException;
import psn.car.rental.reservation.application.InventoryUnavailableException;
import psn.car.rental.reservation.application.NoCarAvailableException;
import psn.car.rental.reservation.domain.InvalidReservationException;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Country;

@DisplayName("Inventory client")
class InventoryClientTest {

  private static final Country POLAND = new Country("pl");
  private static final ReservationId ID =
      new ReservationId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
  private static final String HOLD = "http://inventory/api/v1/holds/" + ID;
  private static final RentalRequest RENTAL =
      new RentalRequest(
          "waw-airport",
          "krk-airport",
          "SEDAN",
          "ECONOMY",
          Instant.parse("2026-12-10T10:00:00Z"),
          3);

  private MockRestServiceServer server;
  private InventoryClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl("http://inventory");
    server = MockRestServiceServer.bindTo(builder).build();
    client = new InventoryClient(builder.build());
  }

  @Test
  void placesAHoldWithTheRentalAndTheCountry() {
    server
        .expect(requestTo(HOLD))
        .andExpect(method(HttpMethod.PUT))
        .andExpect(header("X-Country", "pl"))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.locationId").value("waw-airport"))
        .andExpect(jsonPath("$.returnLocationId").value("krk-airport"))
        .andExpect(jsonPath("$.start").value("2026-12-10T10:00:00Z"))
        .andExpect(jsonPath("$.days").value(3))
        .andRespond(withStatus(HttpStatus.CREATED));

    assertThatCode(() -> client.placeHold(POLAND, ID, RENTAL)).doesNotThrowAnyException();
    server.verify();
  }

  @Test
  void confirmsReleasesAndRegistersReturns() {
    server
        .expect(requestTo(HOLD + "/confirm"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK));
    server.expect(requestTo(HOLD)).andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
    server
        .expect(requestTo(HOLD + "/return"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withNoContent());

    client.confirmHold(POLAND, ID);
    client.releaseHold(POLAND, ID);
    client.registerReturn(POLAND, ID);
    server.verify();
  }

  @Test
  void turnsProblemsIntoErrors() {
    respondToPlace(HttpStatus.CONFLICT, "/problems/no-car-available");
    assertThatThrownBy(() -> client.placeHold(POLAND, ID, RENTAL))
        .isInstanceOf(NoCarAvailableException.class);

    respondToPlace(HttpStatus.CONFLICT, "/problems/conflicting-request");
    assertThatThrownBy(() -> client.placeHold(POLAND, ID, RENTAL))
        .isInstanceOf(ConflictingRequestException.class);

    respondToPlace(HttpStatus.BAD_REQUEST, "/problems/invalid-request");
    assertThatThrownBy(() -> client.placeHold(POLAND, ID, RENTAL))
        .isInstanceOf(InvalidReservationException.class);

    respondToPlace(HttpStatus.INTERNAL_SERVER_ERROR, "/problems/error");
    assertThatThrownBy(() -> client.placeHold(POLAND, ID, RENTAL))
        .isInstanceOf(InventoryUnavailableException.class);
  }

  @Test
  void missingHoldIsReported() {
    server
        .expect(requestTo(HOLD + "/confirm"))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{}"));

    assertThatThrownBy(() -> client.confirmHold(POLAND, ID))
        .isInstanceOf(HoldNotFoundException.class);
  }

  @Test
  void noAnswerMeansTheInventoryIsUnavailable() {
    server.expect(requestTo(HOLD)).andRespond(withException(new SocketTimeoutException("timeout")));
    assertThatThrownBy(() -> client.releaseHold(POLAND, ID))
        .isInstanceOf(InventoryUnavailableException.class);

    server.reset();
    server
        .expect(requestTo(HOLD + "/return"))
        .andRespond(withException(new IOException("refused")));
    assertThatThrownBy(() -> client.registerReturn(POLAND, ID))
        .isInstanceOf(InventoryUnavailableException.class);
  }

  private void respondToPlace(HttpStatus status, String type) {
    server.reset();
    server
        .expect(requestTo(HOLD))
        .andRespond(
            withStatus(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("{\"type\": \"" + type + "\", \"status\": " + status.value() + "}"));
  }
}
