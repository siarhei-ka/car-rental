package psn.car.rental.administration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.service;
import static psn.car.rental.shared.security.TestTokens.superAdmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import psn.car.rental.shared.security.TestTokens;

@SpringBootTest(
    properties = {"administration.seed-data=true", "logging.structured.format.console="})
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, TestTokens.AllRolesByDefault.class})
@DisplayName("Access to the administration service")
class AccessTest {

  @Autowired MockMvc mvc;

  @Test
  void refusesARequestWithoutToken() throws Exception {
    mvc.perform(get("/api/v1/admin/countries").with(anonymous()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void givesTheSnapshotOnlyToServices() throws Exception {
    mvc.perform(get("/api/v1/reference-data").with(service())).andExpect(status().isOk());
    mvc.perform(get("/api/v1/reference-data").with(superAdmin())).andExpect(status().isForbidden());
  }

  @Test
  void letsACountryAdministratorReadButNotChangeSharedData() throws Exception {
    mvc.perform(get("/api/v1/admin/car-types").with(countryAdmin("pl"))).andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/admin/car-types/SEDAN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sedan\",\"active\":true}")
                .with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
    mvc.perform(
            put("/api/v1/admin/countries/pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
  }

  @Test
  void letsACountryAdministratorManageOnlyTheLocationsOfTheirCountry() throws Exception {
    mvc.perform(get("/api/v1/admin/locations").header("X-Country", "pl").with(countryAdmin("pl")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/locations").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
  }

  @Test
  void refusesACustomer() throws Exception {
    mvc.perform(get("/api/v1/admin/countries").with(customer())).andExpect(status().isForbidden());
  }
}
