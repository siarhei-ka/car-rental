package psn.car.rental.reservation.inventory;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import psn.car.rental.reservation.IntegrationTest;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Country;
import psn.car.rental.shared.CorrelationId;

/** The HTTP client that Spring builds passes the correlation ID on to the inventory (NFR-19). */
@IntegrationTest
class CorrelationPropagationTest {

  @Autowired RestClient.Builder builder;

  @AfterEach
  void clearLoggingContext() {
    MDC.clear();
  }

  @Test
  void callsToTheInventoryCarryTheCorrelationIdOfTheRequest() {
    RestClient.Builder inventoryBuilder = builder.baseUrl("http://inventory");
    MockRestServiceServer server = MockRestServiceServer.bindTo(inventoryBuilder).build();
    ReservationId id = new ReservationId(UUID.fromString("11111111-2222-3333-4444-555555555555"));
    server
        .expect(requestTo("http://inventory/api/v1/holds/" + id + "/confirm"))
        .andExpect(header(CorrelationId.HEADER, "trace-42"))
        .andRespond(withStatus(HttpStatus.OK));
    MDC.put(CorrelationId.MDC_KEY, "trace-42");

    new InventoryClient(inventoryBuilder.build()).confirmHold(new Country("pl"), id);

    server.verify();
  }
}
