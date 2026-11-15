package psn.car.rental.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import psn.car.rental.inventory.application.HoldExpiryJob;

@IntegrationTest
@DisplayName("Inventory API")
class InventoryApiTest {

  private static final String START = "2026-12-10T10:00:00Z";

  @Autowired MockMvc mvc;
  @Autowired TestInfrastructure.MutableClock clock;
  @Autowired HoldExpiryJob expiryJob;

  @Nested
  @DisplayName("Holds")
  class Holds {

    @Test
    void placesConfirmsAndReleasesAHold() throws Exception {
      setStock("pl", "h-loc-1", "SEDAN", "ECONOMY", 1);

      placeHold("pl", "h-res-1", "h-loc-1", "SEDAN", START, 3, "h-loc-1")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value("h-res-1"))
          .andExpect(jsonPath("$.start").value(START))
          .andExpect(jsonPath("$.end").value("2026-12-13T10:00:00Z"))
          .andExpect(jsonPath("$.oneWay").value(false))
          .andExpect(jsonPath("$.status").value("PROVISIONAL"));
      availableCars("pl", "h-loc-1", START, 1).andExpect(jsonPath("$[0].availableCars").value(0));

      mvc.perform(post("/api/v1/holds/h-res-1/confirm").header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("CONFIRMED"));

      mvc.perform(delete("/api/v1/holds/h-res-1").header("X-Country", "pl"))
          .andExpect(status().isNoContent());
      availableCars("pl", "h-loc-1", START, 1).andExpect(jsonPath("$[0].availableCars").value(1));
      mvc.perform(delete("/api/v1/holds/h-res-1").header("X-Country", "pl"))
          .andExpect(status().isNoContent());
    }

    @Test
    void repeatingTheSameHoldRequestReturnsTheExistingHold() throws Exception {
      setStock("pl", "h-loc-2", "SUV", "ECONOMY", 1);

      placeHold("pl", "h-res-2", "h-loc-2", "SUV", START, 2, "h-loc-2")
          .andExpect(status().isCreated());
      placeHold("pl", "h-res-2", "h-loc-2", "SUV", START, 2, "h-loc-2")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("h-res-2"));
      placeHold("pl", "h-res-2", "h-loc-2", "SUV", START, 3, "h-loc-2")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/conflicting-request"));
    }

    @Test
    void rejectsAHoldWhenNoCarIsFree() throws Exception {
      setStock("pl", "h-loc-3", "VAN", "ECONOMY", 1);
      placeHold("pl", "h-res-3a", "h-loc-3", "VAN", START, 3, "h-loc-3")
          .andExpect(status().isCreated());

      placeHold("pl", "h-res-3b", "h-loc-3", "VAN", "2026-12-11T10:00:00Z", 1, "h-loc-3")
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value("/problems/no-car-available"))
          .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rejectsAHoldForCarsThatAreNotStocked() throws Exception {
      placeHold("pl", "h-res-4", "h-loc-unknown", "SEDAN", START, 1, "h-loc-unknown")
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/no-car-available"));
    }

    @Test
    void unknownHoldCannotBeConfirmedOrReturned() throws Exception {
      mvc.perform(post("/api/v1/holds/h-missing/confirm").header("X-Country", "pl"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("/problems/not-found"));
      mvc.perform(post("/api/v1/holds/h-missing/return").header("X-Country", "pl"))
          .andExpect(status().isNotFound());
    }

    @Test
    void provisionalHoldIsReleasedWhenItIsNotConfirmedInTime() throws Exception {
      setStock("pl", "h-loc-5", "SEDAN", "ECONOMY", 1);
      placeHold("pl", "h-res-5", "h-loc-5", "SEDAN", START, 1, "h-loc-5")
          .andExpect(status().isCreated());

      clock.advance(Duration.ofMinutes(3));
      expiryJob.releaseExpiredHolds();

      availableCars("pl", "h-loc-5", START, 1).andExpect(jsonPath("$[0].availableCars").value(1));
      mvc.perform(post("/api/v1/holds/h-res-5/confirm").header("X-Country", "pl"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("One-way rentals and transfers")
  class OneWay {

    @Test
    void returnOfAOneWayRentalMovesTheCarToTheReturnLocation() throws Exception {
      setStock("pl", "ow-a", "SEDAN", "ECONOMY", 1);

      placeHold("pl", "ow-res-1", "ow-a", "SEDAN", START, 2, "ow-b")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.oneWay").value(true));
      availableCars("pl", "ow-a", "2026-12-20T10:00:00Z", 1)
          .andExpect(jsonPath("$[0].availableCars").value(0));

      mvc.perform(post("/api/v1/holds/ow-res-1/return").header("X-Country", "pl"))
          .andExpect(status().isNoContent());

      stockOf("pl", "ow-a").andExpect(jsonPath("$[0].cars").value(0));
      stockOf("pl", "ow-b").andExpect(jsonPath("$[0].cars").value(1));
    }

    @Test
    void transferMovesCarsWhenItsArrivalIsRegistered() throws Exception {
      setStock("pl", "tr-a", "SUV", "ECONOMY", 3);

      mvc.perform(
              put("/api/v1/admin/transfers/tr-1")
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"locationId": "tr-a", "carType": "SUV", "carClass": "ECONOMY", "cars": 2,
                       "departure": "2026-12-05T08:00:00Z", "targetLocationId": "tr-b",
                       "plannedArrival": "2026-12-05T18:00:00Z"}
                      """))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.cars").value(2));
      availableCars("pl", "tr-a", START, 1).andExpect(jsonPath("$[0].availableCars").value(1));
      mvc.perform(
              get("/api/v1/admin/transfers").header("X-Country", "pl").param("locationId", "tr-a"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(1)))
          .andExpect(jsonPath("$[0].id").value("tr-1"))
          .andExpect(jsonPath("$[0].targetLocationId").value("tr-b"));

      mvc.perform(post("/api/v1/admin/transfers/tr-1/arrival").header("X-Country", "pl"))
          .andExpect(status().isNoContent());

      stockOf("pl", "tr-a").andExpect(jsonPath("$[0].cars").value(1));
      stockOf("pl", "tr-b").andExpect(jsonPath("$[0].cars").value(2));
      mvc.perform(
              get("/api/v1/admin/transfers").header("X-Country", "pl").param("locationId", "tr-a"))
          .andExpect(jsonPath("$", hasSize(0)));
      mvc.perform(get("/api/v1/admin/transfers").header("X-Country", "pl"))
          .andExpect(status().isOk());
      mvc.perform(post("/api/v1/admin/transfers/tr-1/arrival").header("X-Country", "pl"))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Stock administration")
  class Stock {

    @Test
    void setsAndListsStock() throws Exception {
      setStock("pl", "st-loc-1", "VAN", "ECONOMY", 4);
      setStock("pl", "st-loc-1", "SEDAN", "PREMIUM", 2);

      stockOf("pl", "st-loc-1")
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].carType").value("SEDAN"))
          .andExpect(jsonPath("$[0].carClass").value("PREMIUM"))
          .andExpect(jsonPath("$[1].cars").value(4));
      mvc.perform(get("/api/v1/admin/stock").header("X-Country", "pl")).andExpect(status().isOk());
    }

    @Test
    void rejectsLoweringStockBelowTheCarsThatAreHeld() throws Exception {
      setStock("pl", "st-loc-2", "SEDAN", "ECONOMY", 1);
      placeHold("pl", "st-res-1", "st-loc-2", "SEDAN", START, 1, "st-loc-2")
          .andExpect(status().isCreated());

      mvc.perform(
              put("/api/v1/admin/stock/st-loc-2/SEDAN/ECONOMY")
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"cars\": 0}"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/stock-in-use"));
    }
  }

  @Nested
  @DisplayName("Countries as tenants")
  class Tenants {

    @Test
    void dataOfOneCountryIsNotVisibleInAnother() throws Exception {
      setStock("pl", "tn-loc-1", "SEDAN", "ECONOMY", 5);

      stockOf("de", "tn-loc-1").andExpect(jsonPath("$", hasSize(0)));
      placeHold("de", "tn-res-1", "tn-loc-1", "SEDAN", START, 1, "tn-loc-1")
          .andExpect(status().isConflict());
      stockOf("pl", "tn-loc-1").andExpect(jsonPath("$[0].cars").value(5));
    }

    @Test
    void requestWithoutAServedCountryIsRejected() throws Exception {
      mvc.perform(get("/api/v1/admin/stock"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
      mvc.perform(get("/api/v1/admin/stock").header("X-Country", "fr"))
          .andExpect(status().isBadRequest());
      mvc.perform(get("/api/v1/admin/stock").header("X-Country", "PL")).andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Validation and cross-cutting behaviour")
  class CrossCutting {

    @Test
    void rejectsInvalidRequests() throws Exception {
      mvc.perform(
              put("/api/v1/holds/v-res-1")
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"locationId\": \"v-loc\", \"carType\": \"sedan\", \"days\": 0}"))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
      mvc.perform(get("/api/v1/holds/bad id/confirm").header("X-Country", "pl"))
          .andExpect(status().is4xxClientError());
      mvc.perform(
              get("/api/v1/availability")
                  .header("X-Country", "pl")
                  .param("locationId", "v-loc")
                  .param("start", "not-a-date")
                  .param("days", "1"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void returnsTheCorrelationIdOfTheCallerOrCreatesOne() throws Exception {
      mvc.perform(
              get("/api/v1/admin/stock")
                  .header("X-Country", "pl")
                  .header("X-Correlation-Id", "abc-123"))
          .andExpect(header().string("X-Correlation-Id", "abc-123"));
      String created =
          mvc.perform(get("/api/v1/admin/stock").header("X-Country", "pl"))
              .andReturn()
              .getResponse()
              .getHeader("X-Correlation-Id");
      assertThat(created).isNotBlank();
    }

    @Test
    void publishesItsOpenApiDescriptionAndHealth() throws Exception {
      mvc.perform(get("/openapi.yaml"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("title: Inventory Service")));
      mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
  }

  private void setStock(String country, String location, String type, String carClass, int cars)
      throws Exception {
    mvc.perform(
            put("/api/v1/admin/stock/{location}/{type}/{carClass}", location, type, carClass)
                .header("X-Country", country)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cars\": " + cars + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.cars").value(cars));
  }

  private ResultActions placeHold(
      String country,
      String id,
      String location,
      String type,
      String start,
      int days,
      String returnLocation)
      throws Exception {
    return mvc.perform(
        put("/api/v1/holds/{id}", id)
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"locationId": "%s", "carType": "%s", "carClass": "ECONOMY", "start": "%s",
                 "days": %d, "returnLocationId": "%s"}
                """
                    .formatted(location, type, start, days, returnLocation)));
  }

  private ResultActions availableCars(String country, String location, String start, int days)
      throws Exception {
    return mvc.perform(
            get("/api/v1/availability")
                .header("X-Country", country)
                .param("locationId", location)
                .param("start", start)
                .param("days", String.valueOf(days)))
        .andExpect(status().isOk());
  }

  private ResultActions stockOf(String country, String location) throws Exception {
    return mvc.perform(
            get("/api/v1/admin/stock").header("X-Country", country).param("locationId", location))
        .andExpect(status().isOk());
  }
}
