package psn.car.rental.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("admin")
@Import(RedisContainer.class)
class AdminGatewayTest {

  static final ServiceStub administration = new ServiceStub();
  static final ServiceStub inventory = new ServiceStub();
  static final ServiceStub pricing = new ServiceStub();
  static final ServiceStub ancillary = new ServiceStub();
  static final ServiceStub discount = new ServiceStub();
  static final ServiceStub reservation = new ServiceStub();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gateway.services.administration", () -> administration.uri().toString());
    registry.add("gateway.services.inventory", () -> inventory.uri().toString());
    registry.add("gateway.services.pricing", () -> pricing.uri().toString());
    registry.add("gateway.services.ancillary", () -> ancillary.uri().toString());
    registry.add("gateway.services.discount", () -> discount.uri().toString());
    registry.add("gateway.services.reservation", () -> reservation.uri().toString());
    registry.add("gateway.keycloak.client-secret", () -> "test-secret");
  }

  @AfterAll
  static void stopStubs() {
    administration.close();
    inventory.close();
    pricing.close();
    ancillary.close();
    discount.close();
    reservation.close();
  }

  @Autowired MockMvc mvc;

  @BeforeEach
  void resetStubs() {
    administration.reset();
    inventory.reset();
  }

  static ClientRegistration keycloak;

  @Autowired
  void keycloak(ClientRegistrationRepository registrations) {
    keycloak = registrations.findByRegistrationId("keycloak");
  }

  static RequestPostProcessor user(String keycloakRole, String country) {
    Map<String, Object> claims =
        country == null
            ? Map.of("sub", "user-1", "roles", List.of(keycloakRole), "name", "Anna Admin")
            : Map.of(
                "sub",
                "user-1",
                "roles",
                List.of(keycloakRole),
                "country",
                country,
                "name",
                "Anna Admin");
    String role = "ROLE_" + keycloakRole.toUpperCase().replace('-', '_');
    return oidcLogin()
        .clientRegistration(keycloak)
        .idToken(token -> token.tokenValue("id-token-1").claims(c -> c.putAll(claims)))
        .authorities(new SimpleGrantedAuthority(role));
  }

  /**
   * Sends the CSRF token the way the UI does: from the cookie in the header. (The csrf() helper of
   * Spring Security would replace the token store of the shared test context.)
   */
  static RequestPostProcessor csrf() {
    return request -> {
      request.setCookies(new jakarta.servlet.http.Cookie("ADMIN-XSRF-TOKEN", "token-1"));
      request.addHeader("X-XSRF-TOKEN", "token-1");
      return request;
    };
  }

  static RequestPostProcessor countryAdmin(String country) {
    return user("country-admin", country);
  }

  static RequestPostProcessor superAdmin() {
    return user("super-admin", null);
  }

  @Test
  void tellsTheUiThatNobodyIsSignedInAndSetsTheCsrfCookie() throws Exception {
    mvc.perform(get("/api/v1/session"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signedIn").value(false))
        .andExpect(jsonPath("$.roles").isEmpty())
        .andExpect(cookie().exists("ADMIN-XSRF-TOKEN"));
  }

  @Test
  void describesTheSignedInAdministrator() throws Exception {
    mvc.perform(get("/api/v1/session").with(countryAdmin("PL")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signedIn").value(true))
        .andExpect(jsonPath("$.name").value("Anna Admin"))
        .andExpect(jsonPath("$.roles[0]").value("COUNTRY_ADMIN"))
        .andExpect(jsonPath("$.country").value("pl"));
  }

  @Test
  void refusesAVisitorWithAProblemInsteadOfARedirect() throws Exception {
    mvc.perform(get("/api/v1/admin/stock"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.type").value("/problems/not-signed-in"));
    assertThat(inventory.count()).isZero();
  }

  @Test
  void passesACountryAdministratorsRequestOnWithTheirCountryAndToken() throws Exception {
    mvc.perform(
            get("/api/v1/admin/stock")
                .param("location", "waw-airport")
                .header("Cookie", "CAR_RENTAL_ADMIN_SESSION=abc")
                .with(countryAdmin("pl")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ok").value(true));

    ServiceStub.Received received = inventory.last();
    assertThat(received.path()).isEqualTo("/api/v1/admin/stock");
    assertThat(received.query()).isEqualTo("location=waw-airport");
    assertThat(received.header("X-Country")).isEqualTo("pl");
    assertThat(received.header("Authorization")).startsWith("Bearer ");
    assertThat(received.header("Cookie")).isNull();
  }

  @Test
  void passesRatesExtrasAndTheOneWayFeeOnInTheAdministratorsCountry() throws Exception {
    mvc.perform(get("/api/v1/admin/rates").with(countryAdmin("pl"))).andExpect(status().isOk());
    assertThat(pricing.last().path()).isEqualTo("/api/v1/admin/rates");
    assertThat(pricing.last().header("X-Country")).isEqualTo("pl");

    mvc.perform(
            put("/api/v1/admin/extras/CHILD_SEAT")
                .with(countryAdmin("pl"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    assertThat(ancillary.last().path()).isEqualTo("/api/v1/admin/extras/CHILD_SEAT");
    assertThat(ancillary.last().header("X-Country")).isEqualTo("pl");

    mvc.perform(get("/api/v1/admin/one-way-fee").header("X-Country", "de").with(superAdmin()))
        .andExpect(status().isOk());
    assertThat(ancillary.last().path()).isEqualTo("/api/v1/admin/one-way-fee");
    assertThat(ancillary.last().header("X-Country")).isEqualTo("de");

    mvc.perform(get("/api/v1/admin/rates").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
  }

  @Test
  void passesReservationsOnInTheAdministratorsCountry() throws Exception {
    mvc.perform(get("/api/v1/admin/reservations").with(countryAdmin("pl")))
        .andExpect(status().isOk());
    assertThat(reservation.last().path()).isEqualTo("/api/v1/admin/reservations");
    assertThat(reservation.last().header("X-Country")).isEqualTo("pl");

    mvc.perform(
            post("/api/v1/admin/reservations/{id}/pick-up", "8e0e6b9e-4a9b-4c3e-9a51-0c6c4b0e1f11")
                .with(countryAdmin("pl"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertThat(reservation.last().path()).endsWith("/pick-up");

    mvc.perform(
            get("/api/v1/admin/reservations").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/reservations").with(superAdmin())).andExpect(status().isForbidden());
  }

  @Test
  void passesDiscountRulesOnInTheAdministratorsCountry() throws Exception {
    mvc.perform(
            post("/api/v1/admin/discount-rules")
                .with(countryAdmin("pl"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    assertThat(discount.last().path()).isEqualTo("/api/v1/admin/discount-rules");
    assertThat(discount.last().header("X-Country")).isEqualTo("pl");

    mvc.perform(
            get("/api/v1/admin/discount-rules").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/discount-evaluations").with(superAdmin()).with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void refusesACountryAdministratorInAnotherCountry() throws Exception {
    mvc.perform(get("/api/v1/admin/locations").header("X-Country", "de").with(countryAdmin("pl")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.detail", containsString("pl")));
    assertThat(administration.count()).isZero();
  }

  @Test
  void refusesACountryAdministratorWithoutACountry() throws Exception {
    mvc.perform(get("/api/v1/admin/transfers").with(countryAdmin(null)))
        .andExpect(status().isForbidden());
    assertThat(inventory.count()).isZero();
  }

  @Test
  void letsASuperAdministratorChooseTheCountry() throws Exception {
    mvc.perform(get("/api/v1/admin/locations").header("X-Country", "de").with(superAdmin()))
        .andExpect(status().isOk());
    assertThat(administration.last().header("X-Country")).isEqualTo("de");
  }

  @Test
  void letsOnlyASuperAdministratorChangeSharedReferenceData() throws Exception {
    String body = "{\"name\":\"France\",\"currency\":\"EUR\",\"active\":true}";
    mvc.perform(
            put("/api/v1/admin/countries/fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .with(countryAdmin("pl")))
        .andExpect(status().isForbidden());
    assertThat(administration.count()).isZero();

    mvc.perform(
            put("/api/v1/admin/countries/fr")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .with(csrf())
                .with(superAdmin()))
        .andExpect(status().isOk());
    assertThat(administration.last().method()).isEqualTo("PUT");
    assertThat(administration.last().path()).isEqualTo("/api/v1/admin/countries/fr");
  }

  @Test
  void letsACountryAdministratorReadSharedReferenceData() throws Exception {
    mvc.perform(get("/api/v1/admin/car-types").with(countryAdmin("pl"))).andExpect(status().isOk());
    assertThat(administration.last().header("X-Country")).isNull();
  }

  @Test
  void refusesAChangeWithoutTheCsrfToken() throws Exception {
    mvc.perform(
            put("/api/v1/admin/car-types/SEDAN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sedan\",\"active\":true}")
                .with(superAdmin()))
        .andExpect(status().isForbidden());
    assertThat(administration.count()).isZero();
  }

  @Test
  void refusesAChangeWithAWrongCsrfToken() throws Exception {
    mvc.perform(
            put("/api/v1/admin/car-types/SEDAN")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Sedan\",\"active\":true}")
                .cookie(new jakarta.servlet.http.Cookie("ADMIN-XSRF-TOKEN", "token-1"))
                .header("X-XSRF-TOKEN", "token-2")
                .with(superAdmin()))
        .andExpect(status().isForbidden());
    assertThat(administration.count()).isZero();
  }

  @Test
  void refusesACustomer() throws Exception {
    mvc.perform(get("/api/v1/admin/stock").with(user("customer", null)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/session").with(user("customer", null)))
        .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"));
  }

  @Test
  void refusesPathsWithoutARoute() throws Exception {
    mvc.perform(get("/api/v1/search").with(superAdmin())).andExpect(status().isForbidden());
  }

  @Test
  void startsTheSignInAtKeycloakWithPkce() throws Exception {
    mvc.perform(get("/oauth2/authorization/keycloak"))
        .andExpect(status().is3xxRedirection())
        .andExpect(
            header()
                .string(
                    "Location",
                    startsWith(
                        "http://localhost:8180/realms/car-rental/protocol/openid-connect/auth?")))
        .andExpect(header().string("Location", containsString("code_challenge_method=S256")));
  }

  @Test
  void signsOutAtKeycloakToo() throws Exception {
    mvc.perform(post("/logout").with(csrf()).with(superAdmin()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.redirectTo")
                .value(
                    startsWith(
                        "http://localhost:8180/realms/car-rental/protocol/openid-connect/logout?id_token_hint=id-token-1")))
        .andExpect(
            jsonPath("$.redirectTo")
                .value(containsString("post_logout_redirect_uri=http://localhost/")));
  }
}
