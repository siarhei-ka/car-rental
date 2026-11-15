package psn.car.rental.discount;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;
import static psn.car.rental.shared.security.TestTokens.service;

import com.jayway.jsonpath.JsonPath;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@DisplayName("Discount rules API")
class DiscountApiTest {

  @Autowired MockMvc mvc;

  static String rule(
      String name, String conditions, String effect, String validTo, boolean active) {
    return """
    {"name":"%s","conditions":%s,"effect":%s,"validFrom":"2026-01-01","validTo":%s,"active":%s}
    """
        .formatted(
            name, conditions, effect, validTo == null ? "null" : "\"" + validTo + "\"", active);
  }

  ResultActions create(String country, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/admin/discount-rules")
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  ResultActions evaluate(String country, String body) throws Exception {
    return mvc.perform(
        post("/api/v1/discount-evaluations")
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
  }

  @Nested
  @DisplayName("Rules")
  class Rules {

    @Test
    void createsARuleInTheCurrencyOfTheCountryAndListsIt() throws Exception {
      create(
              "de",
              rule(
                  "Seats: 15 off",
                  "{\"requiredExtras\":[\"CHILD_SEAT\",\"BOOSTER_SEAT\"],\"minDays\":3}",
                  "{\"kind\":\"AMOUNT_OFF\",\"amount\":\"15\",\"target\":\"EXTRAS\"}",
                  "2026-12-31",
                  true))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").isNotEmpty())
          .andExpect(jsonPath("$.effect.amount.amount").value("15.00"))
          .andExpect(jsonPath("$.effect.amount.currency").value("EUR"))
          .andExpect(jsonPath("$.effect.percent").doesNotExist())
          .andExpect(
              jsonPath(
                  "$.conditions.requiredExtras",
                  Matchers.containsInAnyOrder("CHILD_SEAT", "BOOSTER_SEAT")))
          .andExpect(jsonPath("$.validTo").value("2026-12-31"));

      mvc.perform(get("/api/v1/admin/discount-rules").header("X-Country", "de"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.name == 'Seats: 15 off')].conditions.minDays").value(3));
      mvc.perform(get("/api/v1/admin/discount-rules").header("X-Country", "pl"))
          .andExpect(jsonPath("$[*].name", Matchers.not(Matchers.hasItem("Seats: 15 off"))));
    }

    @Test
    void changesARuleAndSwitchesItOff() throws Exception {
      String body =
          create(
                  "pl",
                  rule(
                      "Two weeks",
                      "{\"minDays\":14}",
                      "{\"kind\":\"PERCENT_OFF\",\"percent\":\"10\",\"target\":\"TOTAL\"}",
                      null,
                      true))
              .andReturn()
              .getResponse()
              .getContentAsString();
      String id = JsonPath.read(body, "$.id");

      mvc.perform(
              put("/api/v1/admin/discount-rules/{id}", id)
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      rule(
                          "Two weeks",
                          "{\"minDays\":14}",
                          "{\"kind\":\"PERCENT_OFF\",\"percent\":\"12.50\",\"target\":\"TOTAL\"}",
                          null,
                          false)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(id))
          .andExpect(jsonPath("$.effect.percent").value("12.5"))
          .andExpect(jsonPath("$.active").value(false));

      mvc.perform(get("/api/v1/admin/discount-rules").header("X-Country", "pl"))
          .andExpect(jsonPath("$[?(@.id == '" + id + "')].active").value(false));
    }

    @Test
    void refusesAnInvalidRule() throws Exception {
      String percent = "{\"kind\":\"PERCENT_OFF\",\"percent\":\"10\",\"target\":\"TOTAL\"}";
      create("pl", rule("No condition", "{}", percent, null, true))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-discount"));
      create(
              "pl",
              rule(
                  "Too much",
                  "{\"minDays\":2}",
                  "{\"kind\":\"PERCENT_OFF\",\"percent\":\"101\",\"target\":\"TOTAL\"}",
                  null,
                  true))
          .andExpect(status().isBadRequest());
      create(
              "pl",
              rule(
                  "Cents",
                  "{\"minDays\":2}",
                  "{\"kind\":\"AMOUNT_OFF\",\"amount\":\"1.001\",\"target\":\"TOTAL\"}",
                  null,
                  true))
          .andExpect(status().isBadRequest());
      create("pl", rule("Backwards", "{\"minDays\":2}", percent, "2025-12-31", true))
          .andExpect(status().isBadRequest());
      create(
              "pl",
              rule(
                  "Kind",
                  "{\"minDays\":2}",
                  "{\"kind\":\"FREE\",\"percent\":\"1\",\"target\":\"TOTAL\"}",
                  null,
                  true))
          .andExpect(status().isBadRequest());
      create("it", rule("Country", "{\"minDays\":2}", percent, null, true))
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
      mvc.perform(
              put("/api/v1/admin/discount-rules/{id}", "00000000-0000-0000-0000-000000000001")
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(rule("Missing", "{\"minDays\":2}", percent, null, true)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("/problems/discount-rule-not-found"));
    }

    @Test
    void onlyAdministratorsOfTheCountryManageRules() throws Exception {
      mvc.perform(
              get("/api/v1/admin/discount-rules")
                  .header("X-Country", "pl")
                  .with(countryAdmin("pl")))
          .andExpect(status().isOk());
      mvc.perform(
              get("/api/v1/admin/discount-rules")
                  .header("X-Country", "de")
                  .with(countryAdmin("pl")))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/admin/discount-rules").header("X-Country", "pl").with(customer()))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/admin/discount-rules").header("X-Country", "pl").with(service()))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/admin/discount-rules").header("X-Country", "pl").with(guest()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Evaluation")
  class Evaluation {

    /** 7 days in Austria with 3 extras costing 60.00 in all; the total is 500.00. */
    static final String BASKET =
        """
        {"date":"%s","days":7,"total":{"amount":"500.00","currency":"EUR"},
         "extras":[{"code":"CHILD_SEAT","quantity":2,"price":{"amount":"40.00","currency":"EUR"}},
                   {"code":"GPS","quantity":1,"price":{"amount":"20.00","currency":"EUR"}}]}
        """;

    @Test
    void givesTheDiscountThatSavesTheMostWhileValid() throws Exception {
      create(
          "at",
          rule(
              "Extras 10%",
              "{\"minExtras\":3}",
              "{\"kind\":\"PERCENT_OFF\",\"percent\":\"10\",\"target\":\"EXTRAS\"}",
              null,
              true)); // 6.00
      create(
          "at",
          rule(
              "Week 5%",
              "{\"minDays\":7}",
              "{\"kind\":\"PERCENT_OFF\",\"percent\":\"5\",\"target\":\"TOTAL\"}",
              "2026-12-15",
              true)); // 25.00
      create(
          "at",
          rule(
              "Off 100",
              "{\"minDays\":1}",
              "{\"kind\":\"AMOUNT_OFF\",\"amount\":\"100\",\"target\":\"TOTAL\"}",
              null,
              false)); // switched off

      evaluate("at", BASKET.formatted("2026-12-10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.discount.name").value("Week 5%"))
          .andExpect(jsonPath("$.discount.target").value("TOTAL"))
          .andExpect(jsonPath("$.discount.amount.amount").value("25.00"))
          .andExpect(jsonPath("$.discount.amount.currency").value("EUR"));
      evaluate("at", BASKET.formatted("2026-12-16"))
          .andExpect(jsonPath("$.discount.name").value("Extras 10%"))
          .andExpect(jsonPath("$.discount.amount.amount").value("6.00"));
    }

    @Test
    void givesNoDiscountWhenNoRuleApplies() throws Exception {
      evaluate(
              "de",
              """
              {"date":"2026-12-10","days":1,"total":{"amount":"50.00","currency":"EUR"}}
              """)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.discount").value(Matchers.nullValue()));
    }

    @Test
    void refusesABasketInAnotherCurrencyOrInconsistent() throws Exception {
      evaluate(
              "pl",
              """
              {"date":"2026-12-10","days":1,"total":{"amount":"50.00","currency":"EUR"}}
              """)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-discount"));
      evaluate(
              "de",
              """
              {"date":"2026-12-10","days":1,"total":{"amount":"5.00","currency":"EUR"},
               "extras":[{"code":"GPS","quantity":1,"price":{"amount":"20.00","currency":"EUR"}}]}
              """)
          .andExpect(status().isBadRequest());
      evaluate("de", "{\"days\":1,\"total\":{\"amount\":\"5\",\"currency\":\"EUR\"}}")
          .andExpect(status().isBadRequest());
    }

    @Test
    void onlyServicesAskForADiscount() throws Exception {
      String body = BASKET.formatted("2026-12-10");
      mvc.perform(
              post("/api/v1/discount-evaluations")
                  .with(service())
                  .header("X-Country", "de")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isOk());
      mvc.perform(
              post("/api/v1/discount-evaluations")
                  .with(customer())
                  .header("X-Country", "de")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden());
      mvc.perform(
              post("/api/v1/discount-evaluations")
                  .with(countryAdmin("de"))
                  .header("X-Country", "de")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isForbidden());
    }
  }
}
