package psn.car.rental.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;
import static psn.car.rental.shared.security.TestTokens.service;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The final price, with every service it depends on stood in for by {@link DependencyStub}. */
@IntegrationTest
@DisplayName("Final price API")
class QuoteApiTest {

  static final DependencyStub stub = DependencyStub.INSTANCE;

  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;

  @BeforeEach
  void rates() throws Exception {
    stub.reset();
    rate("pl", "waw-airport", "149.00");
    rate("de", "ber", "60.00");
  }

  void rate(String country, String location, String amount) throws Exception {
    mvc.perform(
            put("/api/v1/admin/rates/{l}/SEDAN/ECONOMY", location)
                .header("X-Country", country)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":\"" + amount + "\"}"))
        .andExpect(status().isOk());
  }

  /** 3 days from Warsaw Airport, starting on 10 December, with the given parts of the body. */
  static String body(String returnTo, String insurance, String extras) {
    return """
    {"pickUpLocationId":"waw-airport","returnLocationId":%s,"start":"2026-12-10T09:00:00Z",
     "days":3,"carType":"SEDAN","carClass":"ECONOMY","insurance":"%s","extras":%s}
    """
        .formatted(returnTo == null ? "null" : "\"" + returnTo + "\"", insurance, extras);
  }

  ResultActions quote(RequestPostProcessor who, String country, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/quotes")
            .with(who)
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Nested
  @DisplayName("A quote")
  class Quotes {

    @Test
    void hasEveryLineTheDiscountAndTheTotal() throws Exception {
      stub.discount = "{\"name\":\"Seats\",\"amount\":{\"amount\":\"40.00\",\"currency\":\"PLN\"}}";

      quote(
              customer("driver-30"),
              "pl",
              body(
                  "krk",
                  "FULL",
                  "[{\"code\":\"CHILD_SEAT\",\"quantity\":2},{\"code\":\"ROAD_TOLL\",\"quantity\":1}]"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currency").value("PLN"))
          .andExpect(jsonPath("$.lines[0].kind").value("CAR_RENTAL"))
          .andExpect(jsonPath("$.lines[0].unitPrice.amount").value("149.00"))
          .andExpect(jsonPath("$.lines[0].amount.amount").value("447.00"))
          .andExpect(jsonPath("$.lines[1].kind").value("INSURANCE"))
          .andExpect(jsonPath("$.lines[1].name").value("Full cover"))
          .andExpect(jsonPath("$.lines[1].amount.amount").value("150.00"))
          .andExpect(jsonPath("$.lines[2].code").value("CHILD_SEAT"))
          .andExpect(jsonPath("$.lines[2].quantity").value(2))
          .andExpect(jsonPath("$.lines[2].amount.amount").value("150.00"))
          .andExpect(jsonPath("$.lines[3].code").value("ROAD_TOLL"))
          .andExpect(jsonPath("$.lines[3].amount.amount").value("49.00"))
          .andExpect(jsonPath("$.lines[4].kind").value("ONE_WAY_FEE"))
          .andExpect(jsonPath("$.lines[4].amount.amount").value("150.00"))
          .andExpect(jsonPath("$.subtotal.amount").value("946.00"))
          .andExpect(jsonPath("$.discount.name").value("Seats"))
          .andExpect(jsonPath("$.discount.amount.amount").value("40.00"))
          .andExpect(jsonPath("$.total.amount").value("906.00"));

      // The insurance is priced for the driver's age on the start day and licence country.
      String insurance = stub.to("/api/v1/insurance-quotes").getFirst().body();
      assertThat((Integer) JsonPath.read(insurance, "$.driverAge")).isEqualTo(30);
      assertThat((String) JsonPath.read(insurance, "$.licenceCountry")).isEqualTo("pl");
      assertThat(stub.to("/api/v1/profiles/driver-30")).hasSize(1);

      // The discount is asked for the basket, on the day of the calculation.
      DependencyStub.Request discount = stub.to("/api/v1/discount-evaluations").getFirst();
      assertThat(discount.country()).isEqualTo("pl");
      assertThat((String) JsonPath.read(discount.body(), "$.date")).isEqualTo("2026-12-01");
      assertThat((Integer) JsonPath.read(discount.body(), "$.days")).isEqualTo(3);
      assertThat((String) JsonPath.read(discount.body(), "$.total.amount")).isEqualTo("946.00");
      assertThat((String) JsonPath.read(discount.body(), "$.extras[0].price.amount"))
          .isEqualTo("150.00");
      assertThat((Integer) JsonPath.read(discount.body(), "$.extras[0].quantity")).isEqualTo(2);
    }

    @Test
    void withoutExtrasDiscountOrOneWayIsTheCarAndTheInsurance() throws Exception {
      quote(customer("driver-30"), "pl", body(null, "BASIC", "[]"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.lines.length()").value(2))
          .andExpect(jsonPath("$.lines[1].amount.amount").value("30.00"))
          .andExpect(jsonPath("$.discount").value(Matchers.nullValue()))
          .andExpect(jsonPath("$.subtotal.amount").value("477.00"))
          .andExpect(jsonPath("$.total.amount").value("477.00"));
      assertThat(stub.to("/api/v1/one-way-fee")).isEmpty();
      assertThat(stub.to("/api/v1/extras")).isEmpty();
    }

    @Test
    void isInTheCurrencyOfTheCountry() throws Exception {
      quote(
              customer("driver-de"),
              "de",
              """
              {"pickUpLocationId":"ber","start":"2026-12-10T09:00:00Z","days":2,
               "carType":"SEDAN","carClass":"ECONOMY","insurance":"BASIC"}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.currency").value("EUR"))
          .andExpect(jsonPath("$.total.amount").value("140.00"))
          .andExpect(jsonPath("$.total.currency").value("EUR"));
    }
  }

  @Nested
  @DisplayName("Refusals")
  class Refusals {

    @Test
    void aDriverYoungerThanTheMinimumAgeOnTheStartDay() throws Exception {
      quote(customer("driver-20"), "pl", body(null, "BASIC", "[]"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value("/problems/driver-too-young"))
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("at least 21")));
      assertThat(stub.to("/api/v1/insurance-quotes")).isEmpty();
    }

    @Test
    void aCustomerWithoutADriverProfile() throws Exception {
      quote(customer("no-profile"), "pl", body(null, "BASIC", "[]"))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.type").value("/problems/profile-required"));
    }

    @Test
    void aCarWithoutARateAtThePickUpLocation() throws Exception {
      quote(customer("driver-30"), "pl", body(null, "BASIC", "[]").replace("SEDAN", "SUV"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value("/problems/car-not-offered"));
    }

    @Test
    void aOneWayRentalInACountryWithoutAFee() throws Exception {
      quote(
              customer("driver-de"),
              "de",
              """
              {"pickUpLocationId":"ber","returnLocationId":"muc","start":"2026-12-10T09:00:00Z",
               "days":2,"carType":"SEDAN","carClass":"ECONOMY","insurance":"BASIC"}
              """)
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value("/problems/one-way-not-offered"));
    }

    @Test
    void aLocationThatIsSwitchedOff() throws Exception {
      quote(customer("driver-30"), "pl", body("closed", "BASIC", "[]"))
          .andExpect(status().isUnprocessableContent())
          .andExpect(jsonPath("$.type").value("/problems/location-not-offered"));
    }

    @Test
    void anInvalidRequest() throws Exception {
      String valid = body(null, "BASIC", "[]");
      quote(customer("driver-30"), "pl", valid.replace("2026-12-10T09", "2026-11-30T09"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-quote"));
      quote(customer("driver-30"), "pl", valid.replace("\"days\":3", "\"days\":31"))
          .andExpect(status().isBadRequest());
      quote(
              customer("driver-30"),
              "pl",
              body(null, "BASIC", "[{\"code\":\"JETPACK\",\"quantity\":1}]"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("JETPACK")));
      quote(
              customer("driver-30"),
              "pl",
              body(null, "BASIC", "[{\"code\":\"ROAD_TOLL\",\"quantity\":2}]"))
          .andExpect(status().isBadRequest());
      quote(
              customer("driver-30"),
              "pl",
              body(
                  null,
                  "BASIC",
                  "[{\"code\":\"GPS\",\"quantity\":1},{\"code\":\"GPS\",\"quantity\":1}]"))
          .andExpect(status().isBadRequest());
      quote(customer("driver-30"), "pl", body(null, "GOLD", "[]"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("There is no insurance option GOLD"));
      quote(customer("driver-30"), "pl", body("ber", "BASIC", "[]"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("does not exist")));
      quote(customer("driver-30"), "it", valid)
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
    }
  }

  @Nested
  @DisplayName("Failures of other services")
  class Failures {

    @Test
    void noPriceWhenTheInsuranceProviderIsUnavailable() throws Exception {
      stub.failures.put("/api/v1/insurance-quotes", 503);
      quote(customer("driver-30"), "pl", body(null, "BASIC", "[]"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.type").value("/problems/price-unavailable"))
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("insurance")));
    }

    @Test
    void noPriceWhenTheDiscountServiceAnswersLate() throws Exception {
      stub.delays.put("/api/v1/discount-evaluations", 1_500L);
      quote(customer("driver-30"), "pl", body(null, "BASIC", "[]"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("discount")));
    }

    @Test
    void noPriceWhenTheCustomerServiceFails() throws Exception {
      stub.failures.put("/api/v1/profiles/driver-30", 500);
      quote(customer("driver-30"), "pl", body(null, "BASIC", "[]"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.detail").value(Matchers.containsString("customer")));
    }
  }

  @Nested
  @DisplayName("Access")
  class Access {

    @Test
    void onlyASignedInCustomerGetsAQuote() throws Exception {
      String valid = body(null, "BASIC", "[]");
      quote(anonymous(), "pl", valid).andExpect(status().isUnauthorized());
      quote(guest(), "pl", valid).andExpect(status().isForbidden());
      quote(service(), "pl", valid).andExpect(status().isForbidden());
      quote(countryAdmin("pl"), "pl", valid).andExpect(status().isForbidden());
    }

    @Test
    void anotherServiceGetsThePriceForTheCustomerItNames() throws Exception {
      String valid = body(null, "BASIC", "[]");
      mvc.perform(
              post("/api/v1/customers/{id}/quotes", "driver-30")
                  .with(service())
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(valid))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.total.amount").value("477.00"));
      assertThat(stub.to("/api/v1/profiles/driver-30")).hasSize(1);

      mvc.perform(
              post("/api/v1/customers/{id}/quotes", "driver-30")
                  .with(customer("driver-30"))
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(valid))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Analytics events")
  class Events {

    List<String> events(String type) {
      return jdbc.sql(
              "select payload::text from public.outbox where payload->>'type' = :type order by id")
          .param("type", type)
          .query(String.class)
          .list();
    }

    @Test
    void aCustomersQuoteIsRecordedButNotAServicesQuote() throws Exception {
      int before = events("price.quoted").size();
      quote(
              customer("driver-30"),
              "pl",
              body("krk", "FULL", "[{\"code\":\"CHILD_SEAT\",\"quantity\":1}]"))
          .andExpect(status().isOk());
      mvc.perform(
              post("/api/v1/customers/{id}/quotes", "driver-30")
                  .with(service())
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body(null, "BASIC", "[]")))
          .andExpect(status().isOk());

      List<String> quoted = events("price.quoted");
      assertThat(quoted).hasSize(before + 1);
      String event = quoted.getLast();
      assertThat((String) JsonPath.read(event, "$.country")).isEqualTo("pl");
      assertThat((String) JsonPath.read(event, "$.actor.kind")).isEqualTo("CUSTOMER");
      assertThat((String) JsonPath.read(event, "$.actor.id")).isEqualTo("driver-30");
      assertThat((String) JsonPath.read(event, "$.data.returnLocationId")).isEqualTo("krk");
      assertThat((String) JsonPath.read(event, "$.data.insurance")).isEqualTo("FULL");
      assertThat((String) JsonPath.read(event, "$.data.extras[0].code")).isEqualTo("CHILD_SEAT");
      assertThat((String) JsonPath.read(event, "$.data.total.currency")).isEqualTo("PLN");
    }

    @Test
    void aRateChangeIsRecordedAsAnAdministratorsAction() throws Exception {
      mvc.perform(
              put("/api/v1/admin/rates/{l}/VAN/ECONOMY", "waw-airport")
                  .with(countryAdmin("pl"))
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"amount\":\"259.00\"}"))
          .andExpect(status().isOk());

      String event = events("admin.rate-changed").getLast();
      assertThat((String) JsonPath.read(event, "$.actor.kind")).isEqualTo("ADMINISTRATOR");
      assertThat((String) JsonPath.read(event, "$.data.carType")).isEqualTo("VAN");
      assertThat((String) JsonPath.read(event, "$.data.dailyRate.amount")).isEqualTo("259.00");
    }
  }
}
