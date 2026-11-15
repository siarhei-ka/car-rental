package psn.car.rental.pricing;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;
import static psn.car.rental.shared.security.TestTokens.service;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
@DisplayName("Daily rates API")
class RateApiTest {

  @Autowired MockMvc mvc;

  @Nested
  @DisplayName("Rates")
  class Rates {

    @Test
    void savesARateInTheCurrencyOfTheCountryAndListsIt() throws Exception {
      save("pl", "r-loc-1", "SEDAN", "ECONOMY", "149")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dailyRate.amount").value("149.00"))
          .andExpect(jsonPath("$.dailyRate.currency").value("PLN"));
      save("pl", "r-loc-1", "SUV", "ECONOMY", "219.50").andExpect(status().isOk());
      save("pl", "r-loc-2", "SEDAN", "ECONOMY", "99").andExpect(status().isOk());

      mvc.perform(get("/api/v1/rates").header("X-Country", "pl").param("locationId", "r-loc-1"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$", Matchers.hasSize(2)))
          .andExpect(jsonPath("$[0].carType").value("SEDAN"))
          .andExpect(jsonPath("$[1].dailyRate.amount").value("219.50"));
      mvc.perform(get("/api/v1/admin/rates").header("X-Country", "pl"))
          .andExpect(jsonPath("$[?(@.locationId == 'r-loc-2')].dailyRate.amount").value("99.00"));
    }

    @Test
    void replacesTheRateOfTheSameCar() throws Exception {
      save("pl", "r-loc-3", "VAN", "ECONOMY", "200").andExpect(status().isOk());
      save("pl", "r-loc-3", "VAN", "ECONOMY", "250").andExpect(status().isOk());

      mvc.perform(get("/api/v1/rates").header("X-Country", "pl").param("locationId", "r-loc-3"))
          .andExpect(jsonPath("$", Matchers.hasSize(1)))
          .andExpect(jsonPath("$[0].dailyRate.amount").value("250.00"));
    }

    @Test
    void keepsTheRatesOfEachCountryApart() throws Exception {
      save("de", "r-loc-4", "SEDAN", "ECONOMY", "39")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dailyRate.currency").value("EUR"));

      mvc.perform(get("/api/v1/rates").header("X-Country", "pl").param("locationId", "r-loc-4"))
          .andExpect(content().json("[]"));
    }

    @Test
    void refusesAnInvalidAmount() throws Exception {
      save("pl", "r-loc-5", "SEDAN", "ECONOMY", "0")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-price"))
          .andExpect(jsonPath("$.detail").value("A daily rate is more than zero"));
      save("pl", "r-loc-5", "SEDAN", "ECONOMY", "10.001")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("An amount in PLN has at most 2 fraction digits"));
      save("pl", "r-loc-5", "SEDAN", "ECONOMY", "cheap").andExpect(status().isBadRequest());
      save("pl", "r-loc-5", "sedan", "ECONOMY", "10").andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUnknownCountry() throws Exception {
      save("it", "r-loc-6", "SEDAN", "ECONOMY", "10")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
      mvc.perform(get("/api/v1/rates").param("locationId", "r-loc-6"))
          .andExpect(status().isBadRequest());
    }

    @Test
    void needsALocationForTheListPrices() throws Exception {
      mvc.perform(get("/api/v1/rates").header("X-Country", "pl"))
          .andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Access")
  class Access {

    @Test
    void showsListPricesToEveryValidToken() throws Exception {
      for (var token : java.util.List.of(guest(), customer(), service())) {
        mvc.perform(listPrices().with(token)).andExpect(status().isOk());
      }
      mvc.perform(listPrices().with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test
    void letsOnlyAdministratorsOfTheCountryChangeRates() throws Exception {
      mvc.perform(saveRequest("pl", "r-loc-7", "10").with(countryAdmin("pl")))
          .andExpect(status().isOk());
      mvc.perform(saveRequest("de", "r-loc-7", "10").with(countryAdmin("pl")))
          .andExpect(status().isForbidden());
      mvc.perform(saveRequest("pl", "r-loc-7", "10").with(customer()))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/admin/rates").header("X-Country", "pl").with(guest()))
          .andExpect(status().isForbidden());
    }

    private MockHttpServletRequestBuilder listPrices() {
      return get("/api/v1/rates").header("X-Country", "pl").param("locationId", "r-loc-1");
    }
  }

  private ResultActions save(
      String country, String location, String carType, String carClass, String amount)
      throws Exception {
    return mvc.perform(
        put("/api/v1/admin/rates/{location}/{type}/{class}", location, carType, carClass)
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":\"" + amount + "\"}"));
  }

  private static MockHttpServletRequestBuilder saveRequest(
      String country, String location, String amount) {
    return put("/api/v1/admin/rates/{location}/SEDAN/ECONOMY", location)
        .header("X-Country", country)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"amount\":\"" + amount + "\"}");
  }
}
