package psn.car.rental.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("customer")
@Import(RedisContainer.class)
class CustomerGatewayTest {

  static final ServiceStub locationInfo = new ServiceStub();
  static final ServiceStub carTypeInfo = new ServiceStub();
  static final ServiceStub search = new ServiceStub();
  static final ServiceStub keycloak = new ServiceStub();
  static final ServiceStub customer = new ServiceStub();
  static final ServiceStub pricing = new ServiceStub();
  static final ServiceStub ancillary = new ServiceStub();
  static final ServiceStub insurance = new ServiceStub();
  static final ServiceStub reservation = new ServiceStub();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("gateway.services.location-info", () -> locationInfo.uri().toString());
    registry.add("gateway.services.car-type-info", () -> carTypeInfo.uri().toString());
    registry.add("gateway.services.search", () -> search.uri().toString());
    registry.add("gateway.services.customer", () -> customer.uri().toString());
    registry.add("gateway.services.pricing", () -> pricing.uri().toString());
    registry.add("gateway.services.ancillary", () -> ancillary.uri().toString());
    registry.add("gateway.services.insurance", () -> insurance.uri().toString());
    registry.add("gateway.services.reservation", () -> reservation.uri().toString());
    registry.add("gateway.keycloak.client-secret", () -> "test-secret");
    registry.add("gateway.keycloak.internal-url", () -> keycloak.uri().toString());
  }

  @AfterAll
  static void stopStubs() {
    locationInfo.close();
    carTypeInfo.close();
    search.close();
    keycloak.close();
    customer.close();
    pricing.close();
    ancillary.close();
    insurance.close();
    reservation.close();
  }

  @Autowired MockMvc mvc;
  @Autowired ClientRegistrationRepository registrations;

  RequestPostProcessor signedIn(String keycloakRole) {
    return oidcLogin()
        .clientRegistration(registrations.findByRegistrationId("keycloak"))
        .idToken(token ->
            token
                .tokenValue("id-token-1")
                .claims(c -> c.putAll(Map.of("sub", "user-1", "roles", List.of(keycloakRole)))))
        .authorities(
            new SimpleGrantedAuthority("ROLE_" + keycloakRole.toUpperCase().replace('-', '_')));
  }

  /** The CSRF token the way the customer UI sends it: from the cookie in the header. */
  static RequestPostProcessor csrf() {
    return request -> {
      request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", "token-1"));
      request.addHeader("X-XSRF-TOKEN", "token-1");
      return request;
    };
  }

  @Test
  void passesTheDriverProfileOfASignedInCustomerOnWithTheirToken() throws Exception {
    mvc.perform(get("/api/v1/profile").with(signedIn("customer"))).andExpect(status().isOk());
    assertThat(customer.last().path()).isEqualTo("/api/v1/profile");
    assertThat(customer.last().header("Authorization")).isEqualTo("Bearer access-token");
    assertThat(customer.last().header("Cookie")).isNull();

    mvc.perform(
            put("/api/v1/profile")
                .with(signedIn("customer"))
                .with(csrf())
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    assertThat(customer.last().method()).isEqualTo("PUT");
  }

  @Test
  void refusesTheDriverProfileToAnyoneElse() throws Exception {
    customer.reset();
    mvc.perform(get("/api/v1/profile")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/profile").with(signedIn("country-admin")))
        .andExpect(status().isForbidden());
    mvc.perform(put("/api/v1/profile").with(signedIn("customer")).content("{}"))
        .andExpect(status().isForbidden());
    assertThat(customer.count()).isZero();
  }

  @Test
  void passesTheFinalPriceOfASignedInCustomerOnWithTheirToken() throws Exception {
    mvc.perform(
            post("/api/v1/quotes")
                .with(signedIn("customer"))
                .with(csrf())
                .header("X-Country", "pl")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    assertThat(pricing.last().path()).isEqualTo("/api/v1/quotes");
    assertThat(pricing.last().method()).isEqualTo("POST");
    assertThat(pricing.last().header("X-Country")).isEqualTo("pl");
    assertThat(pricing.last().header("Authorization")).isEqualTo("Bearer access-token");
  }

  @Test
  void refusesTheFinalPriceToAnyoneElse() throws Exception {
    pricing.reset();
    mvc.perform(post("/api/v1/quotes").with(csrf()).content("{}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/quotes").with(signedIn("country-admin")).with(csrf()).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/quotes").with(signedIn("customer")).content("{}"))
        .andExpect(status().isForbidden());
    assertThat(pricing.count()).isZero();
  }

  @Test
  void passesReservationsOfASignedInCustomerOnWithTheirToken() throws Exception {
    mvc.perform(
            post("/api/v1/reservations")
                .with(signedIn("customer"))
                .with(csrf())
                .header("X-Country", "pl")
                .header("Idempotency-Key", "key-gateway-01")
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isOk());
    assertThat(reservation.last().path()).isEqualTo("/api/v1/reservations");
    assertThat(reservation.last().header("Idempotency-Key")).isEqualTo("key-gateway-01");
    assertThat(reservation.last().header("Authorization")).isEqualTo("Bearer access-token");

    mvc.perform(get("/api/v1/reservations").with(signedIn("customer"))).andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/reservations/{id}", "8e0e6b9e-4a9b-4c3e-9a51-0c6c4b0e1f11")
                .with(signedIn("customer")))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/reservations/{id}/cancel", "8e0e6b9e-4a9b-4c3e-9a51-0c6c4b0e1f11")
                .with(signedIn("customer"))
                .with(csrf()))
        .andExpect(status().isOk());
    assertThat(reservation.last().path()).endsWith("/cancel");
  }

  @Test
  void refusesReservationsToAnyoneElse() throws Exception {
    reservation.reset();
    mvc.perform(get("/api/v1/reservations")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/reservations").with(signedIn("country-admin")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/api/v1/reservations").with(signedIn("customer")).content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/reservations").with(signedIn("customer")))
        .andExpect(status().isForbidden());
    assertThat(reservation.count()).isZero();
  }

  @Test
  void passesTheCorrelationIdOfEveryRequestOn() throws Exception {
    String id =
        mvc.perform(get("/api/v1/locations"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("X-Correlation-Id");
    assertThat(id).isNotBlank();
    assertThat(locationInfo.last().header("X-Correlation-Id")).isEqualTo(id);

    mvc.perform(get("/api/v1/locations").header("X-Correlation-Id", "from-the-browser"))
        .andExpect(header().string("X-Correlation-Id", "from-the-browser"));
    assertThat(locationInfo.last().header("X-Correlation-Id")).isEqualTo("from-the-browser");
  }

  @Test
  void allowsBrowserCallsOnlyFromTheCustomerUi() throws Exception {
    mvc.perform(
            options("/api/v1/quotes")
                .header("Origin", "http://localhost:8080")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-xsrf-token,x-country"))
        .andExpect(status().isOk())
        .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8080"))
        .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    mvc.perform(
            options("/api/v1/quotes")
                .header("Origin", "http://localhost:8090")
                .header("Access-Control-Request-Method", "POST"))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/locations").header("Origin", "https://evil.example"))
        .andExpect(status().isForbidden());
  }

  @Test
  void passesPublicReadsOnWithTheGatewaysOwnToken() throws Exception {
    mvc.perform(get("/api/v1/locations").header("X-Country", "pl")).andExpect(status().isOk());
    assertThat(locationInfo.last().path()).isEqualTo("/api/v1/locations");
    assertThat(locationInfo.last().header("X-Country")).isEqualTo("pl");
    assertThat(locationInfo.last().header("Authorization"))
        .isEqualTo("Bearer " + ServiceStub.TOKEN);
    assertThat(keycloak.last().path())
        .isEqualTo("/realms/car-rental/protocol/openid-connect/token");

    mvc.perform(get("/api/v1/countries")).andExpect(status().isOk());
    mvc.perform(get("/api/v1/car-classes")).andExpect(status().isOk());
    assertThat(carTypeInfo.last().path()).isEqualTo("/api/v1/car-classes");

    mvc.perform(get("/api/v1/search").param("pickUpLocation", "waw-airport"))
        .andExpect(status().isOk());
    assertThat(search.last().query()).isEqualTo("pickUpLocation=waw-airport");
    assertThat(search.last().header("Authorization")).isEqualTo("Bearer " + ServiceStub.TOKEN);
    assertThat(keycloak.count()).as("the token is reused").isEqualTo(1);
  }

  @Test
  void passesListPricesOnWithoutSignIn() throws Exception {
    mvc.perform(get("/api/v1/rates").param("locationId", "waw-airport").header("X-Country", "pl"))
        .andExpect(status().isOk());
    assertThat(pricing.last().query()).isEqualTo("locationId=waw-airport");
    assertThat(pricing.last().header("Authorization")).isEqualTo("Bearer " + ServiceStub.TOKEN);

    mvc.perform(get("/api/v1/extras").header("X-Country", "pl")).andExpect(status().isOk());
    assertThat(ancillary.last().path()).isEqualTo("/api/v1/extras");
    mvc.perform(get("/api/v1/one-way-fee").header("X-Country", "pl")).andExpect(status().isOk());
    assertThat(ancillary.last().path()).isEqualTo("/api/v1/one-way-fee");
    mvc.perform(get("/api/v1/insurance-options").header("X-Country", "pl"))
        .andExpect(status().isOk());
    assertThat(insurance.last().header("Authorization")).isEqualTo("Bearer " + ServiceStub.TOKEN);
    mvc.perform(post("/api/v1/insurance-quotes").with(signedIn("customer")).with(csrf()))
        .andExpect(status().isForbidden());

    mvc.perform(put("/api/v1/one-way-fee").with(signedIn("customer")).with(csrf()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/admin/rates")).andExpect(status().isUnauthorized());
  }

  @Test
  void refusesOtherPaths() throws Exception {
    mvc.perform(get("/api/v1/admin/stock")).andExpect(status().isUnauthorized());
    mvc.perform(post("/api/v1/search")).andExpect(status().isForbidden());
  }

  @Test
  void usesItsOwnCookieNames() throws Exception {
    mvc.perform(get("/api/v1/session")).andExpect(cookie().exists("XSRF-TOKEN"));
  }
}
