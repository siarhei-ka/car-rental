package psn.car.rental.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import psn.car.rental.reservation.FakeInventory.Failure;
import psn.car.rental.reservation.application.InventorySyncJob;
import psn.car.rental.reservation.application.ReservationService;
import psn.car.rental.reservation.application.ReservationStore;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.Reservation;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.domain.ReservationPolicy;
import psn.car.rental.reservation.tenant.Country;
import psn.car.rental.shared.security.TestTokens;

@IntegrationTest
@DisplayName("Reservation API")
class ReservationApiTest {

  private static final String START = "2026-12-10T10:00:00Z";

  @Autowired MockMvc mvc;
  @Autowired FakeInventory inventory;
  @Autowired FakePricing pricing;
  @Autowired TestInfrastructure.MutableClock clock;
  @Autowired InventorySyncJob syncJob;
  @Autowired ReservationService reservations;
  @Autowired ReservationStore store;
  @Autowired TransactionTemplate transactions;

  @BeforeEach
  void resetInventory() {
    inventory.reset();
    pricing.reset();
  }

  @Nested
  @DisplayName("Creating a reservation")
  class Creating {

    @Test
    void createsAReservationAndConfirmsItsHold() throws Exception {
      inventory.setCars("pl", "c-loc-1", "SEDAN", 1);

      String id =
          id(
              create("pl", "key-create-01", "c-loc-1", "c-loc-1", START, 3)
                  .andExpect(status().isCreated())
                  .andExpect(header().string("Location", startsWith("/api/v1/reservations/")))
                  .andExpect(jsonPath("$.state").value("CONFIRMED"))
                  .andExpect(jsonPath("$.start").value(START))
                  .andExpect(jsonPath("$.end").value("2026-12-13T10:00:00Z"))
                  .andExpect(jsonPath("$.oneWay").value(false))
                  .andExpect(jsonPath("$.paymentMethod").value("PAY_AT_PICK_UP"))
                  .andExpect(jsonPath("$.inventoryUpToDate").value(true)));

      assertThat(inventory.calls()).containsExactly("place:" + id, "confirm:" + id);
      mvc.perform(get("/api/v1/reservations/{id}", id).header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id));
    }

    @Test
    void repeatingARequestWithTheSameKeyCreatesNothingNew() throws Exception {
      inventory.setCars("pl", "c-loc-2", "SEDAN", 5);
      String first = id(create("pl", "key-repeat-01", "c-loc-2", "c-loc-2", START, 2));

      String repeated =
          id(
              create("pl", "key-repeat-01", "c-loc-2", "c-loc-2", START, 2)
                  .andExpect(status().isOk()));

      assertThat(repeated).isEqualTo(first);
      assertThat(inventory.calls()).containsExactly("place:" + first, "confirm:" + first);
      create("pl", "key-repeat-01", "c-loc-2", "c-loc-2", START, 3)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/conflicting-request"));
    }

    @Test
    void concurrentRepeatsOfOneRequestCreateOneReservation() throws Exception {
      inventory.setCars("pl", "c-loc-3", "SEDAN", 10);
      RentalRequest rental =
          new RentalRequest("c-loc-3", "c-loc-3", "SEDAN", "ECONOMY", Instant.parse(START), 2);
      CountDownLatch start = new CountDownLatch(1);
      List<Future<ReservationService.CreatedReservation>> results = new ArrayList<>();
      try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
        for (int i = 0; i < 8; i++) {
          results.add(
              pool.submit(() -> {
                start.await();
                return reservations.create(
                    new Country("pl"),
                    "test-user",
                    "key-concurrent-01",
                    rental,
                    "PLN",
                    new java.math.BigDecimal(FakePricing.total(2)));
              }));
        }
        start.countDown();
        List<ReservationService.CreatedReservation> created = new ArrayList<>();
        for (Future<ReservationService.CreatedReservation> result : results) {
          created.add(result.get());
        }
        assertThat(created)
            .extracting(c -> c.reservation().id())
            .containsOnly(created.getFirst().reservation().id());
        assertThat(created).filteredOn(ReservationService.CreatedReservation::created).hasSize(1);
      }
    }

    @Test
    void rejectsAReservationWhenNoCarIsFree() throws Exception {
      inventory.setCars("pl", "c-loc-4", "VAN", 1);
      create("pl", "key-nocar-01", "c-loc-4", "c-loc-4", START, 1).andExpect(status().isCreated());

      create("pl", "key-nocar-02", "c-loc-4", "c-loc-4", START, 1)
          .andExpect(status().isConflict())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.type").value("/problems/no-car-available"));
    }

    @Test
    void appliesTheReservationRules() throws Exception {
      inventory.setCars("pl", "c-loc-5", "SEDAN", 5);

      create("pl", "key-rules-01", "c-loc-5", "c-loc-5", "2026-11-30T10:00:00Z", 1)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-reservation"));
      create("pl", "key-rules-02", "c-loc-5", "c-loc-5", START, 31)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-reservation"));
      create("pl", "key-rules-03", "c-loc-5", "c-loc-5", START, 30).andExpect(status().isCreated());
      assertThat(inventory.calls()).hasSize(2);
    }

    @Test
    void rejectsARequestWithoutAValidIdempotencyKey() throws Exception {
      mvc.perform(
              post("/api/v1/reservations")
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("c-loc-6", "c-loc-6", START, 1)))
          .andExpect(status().isBadRequest());
      create("pl", "short", "c-loc-6", "c-loc-6", START, 1).andExpect(status().isBadRequest());
      assertThat(inventory.calls()).isEmpty();
    }

    @Test
    void nothingIsReservedWhenTheInventoryIsDown() throws Exception {
      inventory.setCars("pl", "c-loc-7", "SEDAN", 1);
      inventory.failPlace(Failure.UNAVAILABLE);

      create("pl", "key-down-01", "c-loc-7", "c-loc-7", START, 1)
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.type").value("/problems/inventory-unavailable"));

      inventory.failPlace(Failure.NONE);
      create("pl", "key-down-01", "c-loc-7", "c-loc-7", START, 1).andExpect(status().isCreated());
    }

    @Test
    void reservationIsUndoneWhenItsHoldCannotBeConfirmed() throws Exception {
      inventory.setCars("pl", "c-loc-8", "SEDAN", 1);
      inventory.failConfirm(Failure.UNAVAILABLE);

      create("pl", "key-undo-01", "c-loc-8", "c-loc-8", START, 1)
          .andExpect(status().isServiceUnavailable());

      List<String> calls = inventory.calls();
      String id = calls.getFirst().substring("place:".length());
      assertThat(calls).containsExactly("place:" + id, "confirm:" + id, "release:" + id);
      assertThat(inventory.holds(new ReservationId(java.util.UUID.fromString(id)))).isFalse();
      mvc.perform(get("/api/v1/reservations/{id}", id).header("X-Country", "pl"))
          .andExpect(status().isNotFound());

      inventory.failConfirm(Failure.NONE);
      create("pl", "key-undo-01", "c-loc-8", "c-loc-8", START, 1).andExpect(status().isCreated());
    }
  }

  @Nested
  @DisplayName("Price")
  class Pricing {

    @Test
    void storesThePriceInsuranceAndExtrasWithTheReservation() throws Exception {
      inventory.setCars("pl", "p-loc-1", "SEDAN", 1);

      String id =
          id(
              mvc.perform(
                      post("/api/v1/reservations")
                          .header("X-Country", "pl")
                          .header("Idempotency-Key", "key-price-01")
                          .contentType(MediaType.APPLICATION_JSON)
                          .content(
                              """
                              {"pickUpLocationId": "p-loc-1", "returnLocationId": "p-loc-1",
                               "carType": "SEDAN", "carClass": "ECONOMY",
                               "start": "2026-12-10T10:00:00Z", "days": 3, "insurance": "FULL",
                               "extras": [{"code": "GPS", "quantity": 1},
                                          {"code": "CHILD_SEAT", "quantity": 2}],
                               "expectedTotal": {"amount": "300", "currency": "PLN"}}
                              """))
                  .andExpect(status().isCreated())
                  .andExpect(jsonPath("$.country").value("pl"))
                  .andExpect(jsonPath("$.insurance").value("FULL"))
                  .andExpect(jsonPath("$.extras[0].code").value("CHILD_SEAT"))
                  .andExpect(jsonPath("$.extras[0].quantity").value(2))
                  .andExpect(jsonPath("$.price.currency").value("PLN"))
                  .andExpect(jsonPath("$.price.lines[0].amount.amount").value("300.00"))
                  .andExpect(jsonPath("$.price.discount").value(Matchers.nullValue()))
                  .andExpect(jsonPath("$.price.total.amount").value("300.00")));

      assertThat(pricing.calls()).containsExactly("pl:test-user:FULL:2");
      // Later price changes do not alter the stored reservation.
      pricing.perDay("120.00");
      reservation(id)
          .andExpect(jsonPath("$.price.total.amount").value("300.00"))
          .andExpect(jsonPath("$.extras[1].code").value("GPS"));
      mvc.perform(get("/api/v1/reservations"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.id == '" + id + "')].country").value("pl"))
          .andExpect(jsonPath("$[?(@.id == '" + id + "')].price.total.amount").value("300.00"));
    }

    @Test
    void aChangedPriceIsShownAndNothingIsReserved() throws Exception {
      inventory.setCars("pl", "p-loc-2", "SEDAN", 1);
      pricing.perDay("110.00");

      create("pl", "key-price-02", "p-loc-2", "p-loc-2", START, 3)
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/price-changed"))
          .andExpect(jsonPath("$.price.total.amount").value("330.00"))
          .andExpect(jsonPath("$.price.total.currency").value("PLN"));
      assertThat(inventory.calls()).isEmpty();

      // The customer confirms the new price with a new key.
      mvc.perform(
              post("/api/v1/reservations")
                  .header("X-Country", "pl")
                  .header("Idempotency-Key", "key-price-03")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      body("p-loc-2", "p-loc-2", START, 3).replace("\"300.00\"", "\"330.00\"")))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.price.total.amount").value("330.00"));
    }

    @Test
    void aRepeatedRequestKeepsItsFirstPrice() throws Exception {
      inventory.setCars("pl", "p-loc-3", "SEDAN", 1);
      create("pl", "key-price-04", "p-loc-3", "p-loc-3", START, 2).andExpect(status().isCreated());
      pricing.perDay("150.00");

      create("pl", "key-price-04", "p-loc-3", "p-loc-3", START, 2)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.price.total.amount").value("200.00"));
      assertThat(pricing.calls()).hasSize(1);
    }

    @Test
    void aRefusalOfThePricingServiceIsPassedOn() throws Exception {
      inventory.setCars("pl", "p-loc-4", "SEDAN", 1);
      pricing.refuse(
          422, "driver-too-young", "The driver must be at least 21 years old on the start");

      create("pl", "key-price-05", "p-loc-4", "p-loc-4", START, 2)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value("/problems/driver-too-young"))
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("at least 21")));
      assertThat(inventory.calls()).isEmpty();
    }

    @Test
    void nothingIsReservedWhenThePriceIsNotAvailable() throws Exception {
      inventory.setCars("pl", "p-loc-5", "SEDAN", 1);
      pricing.fail();

      create("pl", "key-price-06", "p-loc-5", "p-loc-5", START, 2)
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.type").value("/problems/price-unavailable"));
      assertThat(inventory.calls()).isEmpty();
    }

    @Test
    void aRequestWithoutTheSeenTotalOrInsuranceIsInvalid() throws Exception {
      String withoutTotal =
          body("p-loc-6", "p-loc-6", START, 1).replaceAll(",\\s*\"expectedTotal\"[^}]*}", "");
      mvc.perform(
              post("/api/v1/reservations")
                  .header("X-Country", "pl")
                  .header("Idempotency-Key", "key-price-07")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(withoutTotal))
          .andExpect(status().isBadRequest());
      mvc.perform(
              post("/api/v1/reservations")
                  .header("X-Country", "pl")
                  .header("Idempotency-Key", "key-price-08")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body("p-loc-6", "p-loc-6", START, 1).replace("BASIC", "basic")))
          .andExpect(status().isBadRequest());
      assertThat(pricing.calls()).isEmpty();
    }
  }

  @Nested
  @DisplayName("Life cycle")
  class LifeCycle {

    @Test
    void cancellationBeforeTheStartReleasesTheCar() throws Exception {
      inventory.setCars("pl", "l-loc-1", "SEDAN", 1);
      String id = id(create("pl", "key-cancel-01", "l-loc-1", "l-loc-1", START, 2));

      mvc.perform(post("/api/v1/reservations/{id}/cancel", id).header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("CANCELLED"))
          .andExpect(jsonPath("$.inventoryUpToDate").value(true));

      assertThat(inventory.calls()).endsWith("release:" + id);
      create("pl", "key-cancel-02", "l-loc-1", "l-loc-1", START, 2).andExpect(status().isCreated());
      mvc.perform(post("/api/v1/reservations/{id}/cancel", id).header("X-Country", "pl"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/invalid-state"));
    }

    @Test
    void pickUpAndReturnAreRegisteredWithTheInventory() throws Exception {
      inventory.setCars("pl", "l-loc-2", "SUV", 1);
      String id = id(create("pl", "key-pickup-01", "l-loc-2", "l-loc-3", START, 2));

      admin(id, "pick-up").andExpect(jsonPath("$.state").value("PICKED_UP"));
      admin(id, "return")
          .andExpect(jsonPath("$.state").value("RETURNED"))
          .andExpect(jsonPath("$.oneWay").value(true))
          .andExpect(jsonPath("$.inventoryUpToDate").value(true));

      assertThat(inventory.calls()).endsWith("return:" + id);
    }

    @Test
    void noShowIsPossibleOnlyFromTheStart() throws Exception {
      inventory.setCars("pl", "l-loc-4", "SEDAN", 1);
      String id = id(create("pl", "key-noshow-01", "l-loc-4", "l-loc-4", START, 1));

      mvc.perform(post("/api/v1/admin/reservations/{id}/no-show", id).header("X-Country", "pl"))
          .andExpect(status().isConflict());

      clock.advance(Duration.ofDays(10));
      try {
        admin(id, "no-show").andExpect(jsonPath("$.state").value("NO_SHOW"));
        assertThat(inventory.calls()).endsWith("release:" + id);
      } finally {
        clock.advance(Duration.ofDays(-10));
      }
    }

    @Test
    void listsReservationsOfALocation() throws Exception {
      inventory.setCars("pl", "l-loc-5", "SEDAN", 2);
      create("pl", "key-list-01", "l-loc-5", "l-loc-5", "2026-12-12T10:00:00Z", 1);
      create("pl", "key-list-02", "l-loc-5", "l-loc-5", START, 1);

      mvc.perform(
              get("/api/v1/admin/reservations")
                  .header("X-Country", "pl")
                  .param("locationId", "l-loc-5"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", hasSize(2)))
          .andExpect(jsonPath("$[0].start").value(START));
    }

    @Test
    void unknownReservationIsNotFound() throws Exception {
      mvc.perform(
              post("/api/v1/reservations/{id}/cancel", "00000000-0000-0000-0000-000000000000")
                  .header("X-Country", "pl"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("/problems/not-found"));
      mvc.perform(get("/api/v1/reservations/not-a-uuid").header("X-Country", "pl"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Inventory updates that fail")
  class FailedUpdates {

    @Test
    void failedReleaseIsRetriedUntilTheInventoryAnswers() throws Exception {
      inventory.setCars("pl", "f-loc-1", "SEDAN", 1);
      String id = id(create("pl", "key-retry-01", "f-loc-1", "f-loc-1", START, 2));
      inventory.failRelease(Failure.UNAVAILABLE);

      mvc.perform(post("/api/v1/reservations/{id}/cancel", id).header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("CANCELLED"))
          .andExpect(jsonPath("$.inventoryUpToDate").value(false));

      inventory.failRelease(Failure.NONE);
      syncJob.retryPendingUpdates();
      reservation(id).andExpect(jsonPath("$.inventoryUpToDate").value(false));

      clock.advance(Duration.ofSeconds(31));
      syncJob.retryPendingUpdates();
      reservation(id).andExpect(jsonPath("$.inventoryUpToDate").value(true));
      assertThat(inventory.calls()).endsWith("release:" + id, "release:" + id);
    }

    @Test
    void reservationWhoseHoldExpiredBeforeConfirmationIsCancelled() throws Exception {
      // As if the service had stopped between storing the reservation and confirming the hold.
      Reservation stored =
          Reservation.create(
              ReservationId.forIdempotencyKey("pl", "test-user", "key-crash-01"),
              "test-user",
              new RentalRequest("f-loc-3", "f-loc-3", "SEDAN", "ECONOMY", Instant.parse(START), 1),
              pricing.quote(
                  new Country("pl"),
                  "test-user",
                  new RentalRequest(
                      "f-loc-3", "f-loc-3", "SEDAN", "ECONOMY", Instant.parse(START), 1)),
              new ReservationPolicy(30),
              clock.instant());
      transactions.executeWithoutResult(tx -> {
        store.useTenant(new Country("pl"));
        store.insert(stored, "key-crash-01", "hash");
      });

      clock.advance(Duration.ofSeconds(31));
      syncJob.retryPendingUpdates();

      reservation(stored.id().toString())
          .andExpect(jsonPath("$.state").value("CANCELLED"))
          .andExpect(jsonPath("$.inventoryUpToDate").value(true));
      assertThat(inventory.calls()).containsExactly("confirm:" + stored.id());
    }

    @Test
    void returnWhoseHoldIsAlreadyGoneCountsAsDone() throws Exception {
      inventory.setCars("pl", "f-loc-2", "SEDAN", 1);
      String id = id(create("pl", "key-lost-01", "f-loc-2", "f-loc-2", START, 2));
      inventory.failReturn(Failure.UNAVAILABLE);
      admin(id, "pick-up");
      admin(id, "return").andExpect(jsonPath("$.inventoryUpToDate").value(false));

      inventory.failReturn(Failure.NOT_FOUND);
      clock.advance(Duration.ofSeconds(31));
      syncJob.retryPendingUpdates();

      reservation(id)
          .andExpect(jsonPath("$.state").value("RETURNED"))
          .andExpect(jsonPath("$.inventoryUpToDate").value(true));
    }
  }

  @Nested
  @DisplayName("Countries and cross-cutting behaviour")
  class CrossCutting {

    @Test
    void reservationsOfOneCountryAreNotVisibleInAnother() throws Exception {
      inventory.setCars("pl", "x-loc-1", "SEDAN", 1);
      String id = id(create("pl", "key-tenant-01", "x-loc-1", "x-loc-1", START, 1));

      mvc.perform(get("/api/v1/reservations/{id}", id).header("X-Country", "de"))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/v1/reservations/{id}", id))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
    }

    @Test
    void publishesItsOpenApiDescriptionHealthAndCorrelationId() throws Exception {
      mvc.perform(get("/openapi.yaml"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("title: Reservation Service")));
      mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
      mvc.perform(
              get("/api/v1/admin/reservations")
                  .header("X-Country", "pl")
                  .header("X-Correlation-Id", "corr-1"))
          .andExpect(header().string("X-Correlation-Id", "corr-1"));
    }
  }

  @Nested
  @DisplayName("Reservations of a customer")
  class Ownership {

    @Test
    void anotherCustomerCannotReadOrCancelAReservation() throws Exception {
      inventory.setCars("pl", "o-loc-1", "SEDAN", 1);
      String id =
          id(
              mvc.perform(createAs("owner-a", "key-owner-01", "o-loc-1"))
                  .andExpect(status().isCreated())
                  .andExpect(jsonPath("$.customerId").value("owner-a")));

      mvc.perform(
              get("/api/v1/reservations/{id}", id)
                  .header("X-Country", "pl")
                  .with(TestTokens.customer("owner-b")))
          .andExpect(status().isNotFound());
      mvc.perform(
              post("/api/v1/reservations/{id}/cancel", id)
                  .header("X-Country", "pl")
                  .with(TestTokens.customer("owner-b")))
          .andExpect(status().isNotFound());

      mvc.perform(
              get("/api/v1/reservations/{id}", id)
                  .header("X-Country", "pl")
                  .with(TestTokens.customer("owner-a")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.state").value("CONFIRMED"));
    }

    @Test
    void twoCustomersWithTheSameIdempotencyKeyGetTwoReservations() throws Exception {
      inventory.setCars("pl", "o-loc-2", "SEDAN", 2);

      String first =
          id(
              mvc.perform(createAs("owner-c", "key-shared-01", "o-loc-2"))
                  .andExpect(status().isCreated()));
      String second =
          id(
              mvc.perform(createAs("owner-d", "key-shared-01", "o-loc-2"))
                  .andExpect(status().isCreated()));

      assertThat(second).isNotEqualTo(first);
      mvc.perform(createAs("owner-c", "key-shared-01", "o-loc-2")).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder createAs(String customer, String key, String location) {
      return post("/api/v1/reservations")
          .header("X-Country", "pl")
          .header("Idempotency-Key", key)
          .with(TestTokens.customer(customer))
          .contentType(MediaType.APPLICATION_JSON)
          .content(body(location, location, START, 2));
    }
  }

  private ResultActions create(
      String country, String key, String pickUp, String returnTo, String start, int days)
      throws Exception {
    return mvc.perform(
        post("/api/v1/reservations")
            .header("X-Country", country)
            .header("Idempotency-Key", key)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(pickUp, returnTo, start, days)));
  }

  private static String body(String pickUp, String returnTo, String start, int days) {
    return """
    {"pickUpLocationId": "%s", "returnLocationId": "%s", "carType": "%s",
     "carClass": "ECONOMY", "start": "%s", "days": %d, "insurance": "BASIC",
     "expectedTotal": {"amount": "%s", "currency": "PLN"}}
    """
        .formatted(pickUp, returnTo, carTypeOf(pickUp), start, days, FakePricing.total(days));
  }

  /** Tests choose the car type through their location names, to keep the helpers short. */
  private static String carTypeOf(String location) {
    return switch (location) {
      case "c-loc-4" -> "VAN";
      case "l-loc-2" -> "SUV";
      default -> "SEDAN";
    };
  }

  private ResultActions admin(String id, String action) throws Exception {
    return mvc.perform(
            post("/api/v1/admin/reservations/{id}/" + action, id).header("X-Country", "pl"))
        .andExpect(status().isOk());
  }

  private ResultActions reservation(String id) throws Exception {
    return mvc.perform(get("/api/v1/reservations/{id}", id).header("X-Country", "pl"))
        .andExpect(status().isOk());
  }

  private static String id(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }
}
