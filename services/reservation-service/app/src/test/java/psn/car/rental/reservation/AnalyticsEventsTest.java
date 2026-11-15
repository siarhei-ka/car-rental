package psn.car.rental.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import psn.car.rental.shared.security.TestTokens;

/** Each reservation event is written to the outbox with its change, and sent to Kafka after. */
@IntegrationTest
@DisplayName("Analytics events of reservations")
class AnalyticsEventsTest {

  @Autowired MockMvc mvc;
  @Autowired FakeInventory inventory;
  @Autowired FakePricing pricing;
  @Autowired JdbcClient jdbc;

  @BeforeEach
  void reset() {
    inventory.reset();
    pricing.reset();
  }

  ResultActions create(String key, String location, String total) throws Exception {
    return mvc.perform(
        post("/api/v1/reservations")
            .with(TestTokens.customer("events-customer"))
            .header("X-Country", "pl")
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"pickUpLocationId": "%1$s", "returnLocationId": "%1$s", "carType": "SEDAN",
                 "carClass": "ECONOMY", "start": "2026-12-10T10:00:00Z", "days": 2,
                 "insurance": "FULL", "extras": [{"code": "GPS", "quantity": 1}],
                 "expectedTotal": {"amount": "%2$s", "currency": "PLN"}}
                """
                    .formatted(location, total)));
  }

  /** The payloads of the events of a type about a location, oldest first. */
  List<String> events(String type, String location) {
    return jdbc.sql(
            "select payload::text from public.outbox where payload->>'type' = :type"
                + " and payload->'data'->>'pickUpLocationId' = :location order by id")
        .param("type", type)
        .param("location", location)
        .query(String.class)
        .list();
  }

  @Test
  void aCreatedReservationIsRecordedAndSentToKafka() throws Exception {
    inventory.setCars("pl", "e-loc-1", "SEDAN", 1);
    String id =
        JsonPath.read(
            create("key-events-01", "e-loc-1", "200.00")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    List<String> created = events("reservation.created", "e-loc-1");
    assertThat(created).hasSize(1);
    String event = created.getFirst();
    assertThat((String) JsonPath.read(event, "$.country")).isEqualTo("pl");
    assertThat((String) JsonPath.read(event, "$.service")).isEqualTo("reservation-service");
    assertThat((String) JsonPath.read(event, "$.actor.kind")).isEqualTo("CUSTOMER");
    assertThat((String) JsonPath.read(event, "$.actor.id")).isEqualTo("events-customer");
    assertThat((String) JsonPath.read(event, "$.data.reservationId")).isEqualTo(id);
    assertThat((String) JsonPath.read(event, "$.data.insurance")).isEqualTo("FULL");
    assertThat((String) JsonPath.read(event, "$.data.extras[0].code")).isEqualTo("GPS");
    assertThat((String) JsonPath.read(event, "$.data.total.amount")).isEqualTo("200.00");
    assertThat(event).doesNotContain("dateOfBirth").doesNotContain("@");

    // A repeated request creates nothing, so it records nothing.
    create("key-events-01", "e-loc-1", "200.00").andExpect(status().isOk());
    assertThat(events("reservation.created", "e-loc-1")).hasSize(1);

    waitUntil(() ->
        jdbc.sql(
                    "select count(*) from public.outbox where payload->'data'->>'reservationId' = :id"
                        + " and published_at is not null")
                .param("id", id)
                .query(Integer.class)
                .single()
            == 1);
  }

  @Test
  void aRefusedReservationIsRecordedWithItsReason() throws Exception {
    inventory.setCars("pl", "e-loc-2", "SEDAN", 0);
    create("key-events-02", "e-loc-2", "200.00").andExpect(status().isConflict());
    pricing.perDay("120.00");
    create("key-events-03", "e-loc-2", "200.00").andExpect(status().isConflict());

    List<String> rejected = events("reservation.rejected", "e-loc-2");
    assertThat(rejected)
        .extracting(e -> (String) JsonPath.read(e, "$.data.reason"))
        .containsExactly("no-car-available", "price-changed");
    assertThat(events("reservation.created", "e-loc-2")).isEmpty();
  }

  @Test
  void aCancellationAndTheDeskActionsAreRecorded() throws Exception {
    inventory.setCars("pl", "e-loc-3", "SEDAN", 2);
    String first =
        JsonPath.read(
            create("key-events-04", "e-loc-3", "200.00")
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");
    String second =
        JsonPath.read(
            create("key-events-05", "e-loc-3", "200.00")
                .andReturn()
                .getResponse()
                .getContentAsString(),
            "$.id");

    mvc.perform(
            post("/api/v1/reservations/{id}/cancel", first)
                .with(TestTokens.customer("events-customer"))
                .header("X-Country", "pl"))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/admin/reservations/{id}/pick-up", second)
                .with(TestTokens.countryAdmin("pl"))
                .header("X-Country", "pl"))
        .andExpect(status().isOk());

    String cancelled = events("reservation.cancelled", "e-loc-3").getFirst();
    assertThat((String) JsonPath.read(cancelled, "$.data.reservationId")).isEqualTo(first);
    assertThat((String) JsonPath.read(cancelled, "$.actor.kind")).isEqualTo("CUSTOMER");
    String pickedUp = events("reservation.picked-up", "e-loc-3").getFirst();
    assertThat((String) JsonPath.read(pickedUp, "$.data.state")).isEqualTo("PICKED_UP");
    assertThat((String) JsonPath.read(pickedUp, "$.actor.kind")).isEqualTo("ADMINISTRATOR");
  }

  private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("The condition was not met within 30 seconds");
      }
      Thread.sleep(100);
    }
  }
}
