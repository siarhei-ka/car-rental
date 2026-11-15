package psn.car.rental.ancillary;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;

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
@DisplayName("Extras and one-way fee API")
class AncillaryApiTest {

  @Autowired MockMvc mvc;

  @Nested
  @DisplayName("Extras")
  class Extras {

    @Test
    void savesExtrasInTheCurrencyOfTheCountryAndOffersOnlyThoseSwitchedOn() throws Exception {
      saveExtra("pl", "A_SEAT", "A seat", "EQUIPMENT", "PER_DAY", "25", 3, true)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.price.amount").value("25.00"))
          .andExpect(jsonPath("$.price.currency").value("PLN"))
          .andExpect(jsonPath("$.maxQuantity").value(3));
      saveExtra("pl", "B_TOLL", "B toll", "SERVICE", "PER_RENTAL", "49.90", 1, true);
      saveExtra("pl", "C_OLD", "C old", "EQUIPMENT", "PER_DAY", "1", 1, false);

      mvc.perform(get("/api/v1/extras").header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[*].code", Matchers.hasItems("A_SEAT", "B_TOLL")))
          .andExpect(jsonPath("$[*].code", Matchers.not(Matchers.hasItem("C_OLD"))))
          .andExpect(jsonPath("$[?(@.code == 'B_TOLL')].charge").value("PER_RENTAL"));
      mvc.perform(get("/api/v1/admin/extras").header("X-Country", "pl"))
          .andExpect(jsonPath("$[?(@.code == 'C_OLD')].active").value(false));
    }

    @Test
    void changesAnExtraAndKeepsTheCountriesApart() throws Exception {
      saveExtra("de", "D_SEAT", "Seat", "EQUIPMENT", "PER_DAY", "8", 2, true);
      saveExtra("de", "D_SEAT", "Child seat", "EQUIPMENT", "PER_DAY", "9.50", 2, true)
          .andExpect(jsonPath("$.price").value(Matchers.hasEntry("currency", "EUR")));

      mvc.perform(get("/api/v1/extras").header("X-Country", "de"))
          .andExpect(jsonPath("$[?(@.code == 'D_SEAT')].name").value("Child seat"))
          .andExpect(jsonPath("$[?(@.code == 'D_SEAT')].price.amount").value("9.50"));
      mvc.perform(get("/api/v1/extras").header("X-Country", "pl"))
          .andExpect(jsonPath("$[*].code", Matchers.not(Matchers.hasItem("D_SEAT"))));
    }

    @Test
    void refusesAnInvalidExtra() throws Exception {
      saveExtra("pl", "E_SEAT", "Seat", "EQUIPMENT", "PER_DAY", "-1", 1, true)
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-price"));
      saveExtra("pl", "E_SEAT", "Seat", "EQUIPMENT", "PER_DAY", "1", 11, true)
          .andExpect(status().isBadRequest());
      saveExtra("pl", "E_SEAT", "Seat", "TOY", "PER_DAY", "1", 1, true)
          .andExpect(status().isBadRequest());
      saveExtra("pl", "e_seat", "Seat", "EQUIPMENT", "PER_DAY", "1", 1, true)
          .andExpect(status().isBadRequest());
      saveExtra("it", "E_SEAT", "Seat", "EQUIPMENT", "PER_DAY", "1", 1, true)
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
    }
  }

  @Nested
  @DisplayName("One-way fee")
  class OneWayFee {

    @Test
    void isMissingUntilSetAndThenPublic() throws Exception {
      mvc.perform(get("/api/v1/one-way-fee").header("X-Country", "de"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("/problems/one-way-fee-not-set"));

      saveFee("de", "50").andExpect(status().isOk()).andExpect(jsonPath("$.amount").value("50.00"));
      saveFee("de", "55.50").andExpect(status().isOk());

      mvc.perform(get("/api/v1/one-way-fee").header("X-Country", "de").with(guest()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.amount").value("55.50"))
          .andExpect(jsonPath("$.currency").value("EUR"));
      mvc.perform(get("/api/v1/admin/one-way-fee").header("X-Country", "de"))
          .andExpect(jsonPath("$.amount").value("55.50"));
    }

    @Test
    void refusesANegativeOrInvalidFee() throws Exception {
      saveFee("pl", "-5")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("The one-way fee is not negative"));
      saveFee("pl", "1.005").andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Access")
  class Access {

    @Test
    void showsTheExtrasToEveryValidTokenOnly() throws Exception {
      mvc.perform(get("/api/v1/extras").header("X-Country", "pl").with(guest()))
          .andExpect(status().isOk());
      mvc.perform(get("/api/v1/extras").header("X-Country", "pl").with(anonymous()))
          .andExpect(status().isUnauthorized());
    }

    @Test
    void letsOnlyAdministratorsOfTheCountryChangeExtrasAndTheFee() throws Exception {
      mvc.perform(fee("pl", "150").with(countryAdmin("pl"))).andExpect(status().isOk());
      mvc.perform(fee("de", "50").with(countryAdmin("pl"))).andExpect(status().isForbidden());
      mvc.perform(fee("pl", "150").with(customer())).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/admin/extras").header("X-Country", "pl").with(guest()))
          .andExpect(status().isForbidden());
    }
  }

  private ResultActions saveExtra(
      String country,
      String code,
      String name,
      String kind,
      String charge,
      String price,
      int maxQuantity,
      boolean active)
      throws Exception {
    return mvc.perform(
        put("/api/v1/admin/extras/{code}", code)
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name":"%s","kind":"%s","charge":"%s","price":"%s","maxQuantity":%d,"active":%s}
                """
                    .formatted(name, kind, charge, price, maxQuantity, active)));
  }

  private ResultActions saveFee(String country, String amount) throws Exception {
    return mvc.perform(fee(country, amount));
  }

  private static MockHttpServletRequestBuilder fee(String country, String amount) {
    return put("/api/v1/admin/one-way-fee")
        .header("X-Country", country)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"amount\":\"" + amount + "\"}");
  }
}
