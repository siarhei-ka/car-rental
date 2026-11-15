package psn.car.rental.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import psn.car.rental.reservation.tenant.Countries;
import psn.car.rental.reservation.tenant.Country;

/** Countries announced by the administration service are served without a restart. */
@IntegrationTest
@DisplayName("Country list")
class CountrySyncTest {

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired MockMvc mvc;
  @Autowired Countries countries;
  @Autowired JdbcClient jdbc;

  @Test
  void aNewCountryIsServedOnceItsEventArrives() throws Exception {
    assertThat(httpStatus("it")).isEqualTo(400);

    kafka.send(
        "reference-data",
        "country:it",
        """
        {"eventId":"c1","kind":"COUNTRY","action":"SAVED",
         "data":{"code":"it","name":"Italy","currency":"EUR","active":true}}
        """);

    waitUntil(() -> countries.isServed(new Country("it")));
    assertThat(httpStatus("it")).isEqualTo(200);
    assertThat(
            jdbc.sql("select to_regclass('tenant_it.reservation') is not null")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void otherAndInvalidEventsAreIgnored() throws Exception {
    kafka.send("reference-data", "x", "not json");
    kafka.send(
        "reference-data",
        "location:pl:x",
        "{\"kind\":\"LOCATION\",\"country\":\"pl\",\"data\":{\"id\":\"x\"}}");
    kafka.send("reference-data", "country:XX", "{\"kind\":\"COUNTRY\",\"data\":{\"code\":\"XX\"}}");
    kafka.send(
        "reference-data",
        "country:es",
        "{\"kind\":\"COUNTRY\",\"data\":{\"code\":\"es\",\"active\":false}}");

    waitUntil(() -> countries.isServed(new Country("es")));
    assertThat(countries.all()).extracting(Country::code).doesNotContain("x", "XX");
    assertThat(httpStatus("es")).isEqualTo(200);
  }

  @Test
  void anInactiveCountryAcceptsNoNewReservations() throws Exception {
    kafka.send(
        "reference-data",
        "country:pt",
        "{\"kind\":\"COUNTRY\",\"data\":{\"code\":\"pt\",\"active\":false}}");
    waitUntil(() -> countries.isServed(new Country("pt")));

    mvc.perform(
            post("/api/v1/reservations")
                .header("X-Country", "pt")
                .header("Idempotency-Key", "key-inactive-01")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"pickUpLocationId": "lis-airport", "returnLocationId": "lis-airport",
                     "carType": "SEDAN", "carClass": "ECONOMY", "start": "2026-12-10T10:00:00Z",
                     "days": 1, "insurance": "BASIC",
                     "expectedTotal": {"amount": "100.00", "currency": "EUR"}}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/problems/unknown-country"))
        .andExpect(jsonPath("$.detail").value("Country pt is not active"));
    assertThat(httpStatus("pt")).isEqualTo(200);
  }

  private int httpStatus(String country) throws Exception {
    return mvc.perform(get("/api/v1/admin/reservations").header("X-Country", country))
        .andReturn()
        .getResponse()
        .getStatus();
  }

  private static void waitUntil(BooleanSupplier condition) {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("The condition was not met within 30 seconds");
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }
}
