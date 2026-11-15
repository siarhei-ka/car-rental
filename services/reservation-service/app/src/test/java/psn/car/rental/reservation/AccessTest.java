package psn.car.rental.reservation;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
@DisplayName("Access to reservations")
class AccessTest {

  @Autowired MockMvc mvc;

  @Test
  void refusesARequestWithoutToken() throws Exception {
    mvc.perform(
            get("/api/v1/reservations/00000000-0000-4000-8000-000000000000")
                .header("X-Country", "pl")
                .with(anonymous()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void letsOnlyCustomersUseReservations() throws Exception {
    mvc.perform(
            get("/api/v1/reservations/00000000-0000-4000-8000-000000000000")
                .header("X-Country", "pl")
                .with(customer()))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/api/v1/reservations/00000000-0000-4000-8000-000000000000")
                .header("X-Country", "pl")
                .with(guest()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations").header("X-Country", "pl").with(superAdmin()))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations").header("X-Country", "pl").with(service()))
        .andExpect(status().isForbidden());
  }

  @Test
  void letsACountryAdministratorWorkOnlyAtTheirDesk() throws Exception {
    mvc.perform(
            get("/api/v1/admin/reservations").header("X-Country", "pl").with(countryAdmin("pl")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/admin/reservations").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/reservations").header("X-Country", "pl").with(customer()))
        .andExpect(status().isForbidden());
  }

  @Test
  void needsNoTokenForTheHealthCheck() throws Exception {
    mvc.perform(get("/actuator/health").with(anonymous())).andExpect(status().isOk());
    mvc.perform(put("/api/v1/unknown").with(superAdmin())).andExpect(status().isForbidden());
  }
}
