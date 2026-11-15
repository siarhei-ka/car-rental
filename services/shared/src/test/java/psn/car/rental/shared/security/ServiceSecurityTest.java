package psn.car.rental.shared.security;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = ServiceSecurityTest.App.class)
@AutoConfigureMockMvc
@DisplayName("Token checks of a service")
class ServiceSecurityTest {

  @Autowired MockMvc mvc;

  @Nested
  @DisplayName("Without a valid token")
  class WithoutToken {

    @Test
    void refusesARequestWithoutToken() throws Exception {
      mvc.perform(get("/api/v1/open"))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", "Bearer"))
          .andExpect(content().contentType("application/problem+json"))
          .andExpect(jsonPath("$.type").value("/problems/not-signed-in"))
          .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void refusesATokenThatIsNotAJwt() throws Exception {
      mvc.perform(get("/api/v1/open").header("Authorization", "Bearer not-a-token"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.type").value("/problems/not-signed-in"));
    }

    @Test
    void refusesAnAnonymousRequest() throws Exception {
      mvc.perform(get("/api/v1/open").with(anonymous())).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("Roles")
  class Roles {

    @Test
    void acceptsAGuestTokenOnAPathForEveryone() throws Exception {
      mvc.perform(get("/api/v1/open").with(TestTokens.guest())).andExpect(status().isOk());
    }

    @Test
    void refusesARoleThatThePathDoesNotAllow() throws Exception {
      mvc.perform(get("/api/v1/admin/things").with(TestTokens.customer()))
          .andExpect(status().isForbidden())
          .andExpect(content().contentType("application/problem+json"))
          .andExpect(jsonPath("$.type").value("/problems/forbidden"));
    }

    @Test
    void letsOnlyServicesUseAnInternalPath() throws Exception {
      mvc.perform(get("/api/v1/internal").with(TestTokens.service())).andExpect(status().isOk());
      mvc.perform(get("/api/v1/internal").with(TestTokens.superAdmin()))
          .andExpect(status().isForbidden());
    }

    @Test
    void refusesAPathWithoutARule() throws Exception {
      mvc.perform(get("/api/v1/unknown").with(TestTokens.superAdmin()))
          .andExpect(status().isForbidden());
    }

    @Test
    void ignoresUnknownRealmRoles() throws Exception {
      mvc.perform(get("/api/v1/admin/things").with(TestTokens.withRoles("offline_access")))
          .andExpect(status().isForbidden());
    }

    @Test
    void doesNotNeedACsrfTokenForAChange() throws Exception {
      mvc.perform(put("/api/v1/admin/things").with(TestTokens.superAdmin()))
          .andExpect(status().isOk());
    }

    @Test
    void keepsTheHealthCheckOpen() throws Exception {
      mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("Country of an administrator")
  class Country {

    @Test
    void letsACountryAdministratorWorkWithTheirCountry() throws Exception {
      mvc.perform(
              get("/api/v1/admin/things")
                  .header("X-Country", "pl")
                  .with(TestTokens.countryAdmin("pl")))
          .andExpect(status().isOk())
          .andExpect(content().string("pl"));
    }

    @Test
    void refusesAnotherCountry() throws Exception {
      mvc.perform(
              get("/api/v1/admin/things")
                  .header("X-Country", "de")
                  .with(TestTokens.countryAdmin("pl")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.detail").value(containsString("pl")));
    }

    @Test
    void refusesACountryAdministratorWithoutACountryInTheToken() throws Exception {
      mvc.perform(
              get("/api/v1/admin/things")
                  .header("X-Country", "pl")
                  .with(TestTokens.withRoles("country-admin")))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.detail").value(containsString("no country")));
    }

    @Test
    void letsASuperAdministratorNameAnyCountry() throws Exception {
      mvc.perform(
              get("/api/v1/admin/things").header("X-Country", "de").with(TestTokens.superAdmin()))
          .andExpect(status().isOk())
          .andExpect(content().string("de"));
    }
  }

  @SpringBootConfiguration
  @EnableAutoConfiguration
  static class App {

    @Bean
    AccessRules rules() {
      return rules ->
          rules
              .requestMatchers(HttpMethod.GET, "/api/v1/open")
              .authenticated()
              .requestMatchers("/api/v1/internal")
              .hasRole(ServiceRoles.SERVICE)
              .requestMatchers("/api/v1/admin/**")
              .hasAnyRole(ServiceRoles.COUNTRY_ADMIN, ServiceRoles.SUPER_ADMIN);
    }

    @Bean
    Endpoints endpoints() {
      return new Endpoints();
    }
  }

  @RestController
  static class Endpoints {

    @GetMapping({"/api/v1/open", "/api/v1/internal", "/api/v1/unknown"})
    String ok() {
      return "ok";
    }

    @GetMapping("/api/v1/admin/things")
    String country(@RequestHeader(name = "X-Country", required = false) String country) {
      return country == null ? "none" : country;
    }

    @PutMapping("/api/v1/admin/things")
    String change() {
      return "changed";
    }
  }
}
