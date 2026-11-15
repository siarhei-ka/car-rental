package psn.car.rental.inventory;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;
import static psn.car.rental.shared.security.TestTokens.service;
import static psn.car.rental.shared.security.TestTokens.superAdmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@DisplayName("Access to the inventory")
class AccessTest {

  @Autowired MockMvc mvc;

  @Test
  void refusesARequestWithoutToken() throws Exception {
    mvc.perform(get("/api/v1/admin/stock").header("X-Country", "pl").with(anonymous()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("/problems/not-signed-in"));
  }

  @Test
  void letsOnlyServicesUseAvailabilityAndHolds() throws Exception {
    mvc.perform(availability().with(service())).andExpect(status().isOk());
    mvc.perform(availability().with(guest())).andExpect(status().isForbidden());
    mvc.perform(availability().with(superAdmin())).andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/holds/a-1/confirm").header("X-Country", "pl").with(customer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void letsACountryAdministratorManageOnlyTheirCountry() throws Exception {
    mvc.perform(get("/api/v1/admin/stock").header("X-Country", "pl").with(countryAdmin("pl")))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/admin/stock").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.type").value("/problems/forbidden"));
    mvc.perform(
            put("/api/v1/admin/stock/a-loc/SEDAN/ECONOMY")
                .header("X-Country", "de")
                .with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
  }

  @Test
  void refusesACustomerOnAdminPaths() throws Exception {
    mvc.perform(get("/api/v1/admin/stock").header("X-Country", "pl").with(customer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void keepsTheHealthCheckOpen() throws Exception {
    mvc.perform(get("/actuator/health").with(anonymous())).andExpect(status().isOk());
    mvc.perform(get("/actuator/metrics").with(guest())).andExpect(status().isForbidden());
  }

  private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
      availability() {
    return get("/api/v1/availability")
        .header("X-Country", "pl")
        .param("locationId", "a-loc")
        .param("start", "2026-12-10T10:00:00Z")
        .param("days", "2");
  }
}
