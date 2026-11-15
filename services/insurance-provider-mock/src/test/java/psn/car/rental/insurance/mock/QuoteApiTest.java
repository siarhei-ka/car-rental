package psn.car.rental.insurance.mock;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = {"insurance-mock.api-key=key-1", "logging.structured.format.console="})
@AutoConfigureMockMvc
@DisplayName("Insurance provider mock")
class QuoteApiTest {

  @Autowired MockMvc mvc;

  @Test
  void givesTheBasePriceWithoutDriverData() throws Exception {
    quote("key-1", "{\"product\":\"BASIC\",\"rentalCountry\":\"pl\",\"currency\":\"PLN\"}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pricePerDay").value("10.00"))
        .andExpect(jsonPath("$.currency").value("PLN"))
        .andExpect(jsonPath("$.factors").isEmpty());
    quote("key-1", "{\"product\":\"full\",\"rentalCountry\":\"de\",\"currency\":\"EUR\"}")
        .andExpect(jsonPath("$.product").value("FULL"))
        .andExpect(jsonPath("$.pricePerDay").value("50.00"));
  }

  @Test
  void appliesTheYoungDriverFactorBelow25() throws Exception {
    quote("key-1", driver("BASIC", 24, "pl")).andExpect(jsonPath("$.pricePerDay").value("15.00"));
    quote("key-1", driver("BASIC", 25, "pl")).andExpect(jsonPath("$.pricePerDay").value("10.00"));
  }

  @Test
  void appliesTheForeignLicenceFactorAndBothTogether() throws Exception {
    quote("key-1", driver("BASIC", 40, "de"))
        .andExpect(jsonPath("$.pricePerDay").value("12.00"))
        .andExpect(jsonPath("$.factors[0]").value("FOREIGN_LICENCE"));
    quote("key-1", driver("FULL", 21, "ua"))
        .andExpect(jsonPath("$.pricePerDay").value("90.00"))
        .andExpect(jsonPath("$.factors.length()").value(2));
  }

  @Test
  void roundsToTheMinorUnitsOfTheCurrency() throws Exception {
    quote(
            "key-1",
            "{\"product\":\"BASIC\",\"rentalCountry\":\"jp\",\"currency\":\"JPY\",\"driverAge\":20}")
        .andExpect(jsonPath("$.pricePerDay").value("15"));
  }

  @Test
  void needsItsApiKey() throws Exception {
    quote("wrong", driver("BASIC", 40, "pl")).andExpect(status().isUnauthorized());
    quote(null, driver("BASIC", 40, "pl")).andExpect(status().isUnauthorized());
  }

  @Test
  void refusesAnInvalidRequest() throws Exception {
    quote("key-1", "{\"product\":\"GOLD\",\"rentalCountry\":\"pl\",\"currency\":\"PLN\"}")
        .andExpect(status().isBadRequest());
    quote("key-1", "{\"product\":\"BASIC\",\"rentalCountry\":\"PL\",\"currency\":\"PLN\"}")
        .andExpect(status().isBadRequest());
    quote("key-1", driver("BASIC", 12, "pl")).andExpect(status().isBadRequest());
  }

  @Test
  void hasAHealthCheck() throws Exception {
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  private ResultActions quote(String key, String body) throws Exception {
    var request = post("/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(body);
    if (key != null) {
      request.header("X-Api-Key", key);
    }
    return mvc.perform(request);
  }

  private static String driver(String product, int age, String licence) {
    return """
    {"product":"%s","rentalCountry":"pl","currency":"PLN","driverAge":%d,"licenceCountry":"%s"}
    """
        .formatted(product, age, licence);
  }
}
