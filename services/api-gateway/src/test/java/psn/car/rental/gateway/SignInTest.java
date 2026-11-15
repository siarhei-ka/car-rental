package psn.car.rental.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import psn.car.rental.shared.security.ServiceRoles;
import psn.car.rental.shared.security.ServiceSecurityProperties;
import psn.car.rental.shared.security.ServiceTokens;
import psn.car.rental.shared.security.TokenValidation;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Signs in through a real Keycloak with the realm of the repository, the way a browser does: the
 * gateway sends the browser to Keycloak, the user fills in the login form, and Keycloak sends the
 * browser back to the gateway.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles("admin")
@Import(RedisContainer.class)
@Testcontainers
class SignInTest {

  static final String PASSWORD = "test-" + UUID.randomUUID();
  static final String SECRET = "secret-" + UUID.randomUUID();
  static final int PORT = freePort();
  static final String GATEWAY = "http://localhost:" + PORT;

  @Container
  static final GenericContainer<?> keycloak =
      new GenericContainer<>("quay.io/keycloak/keycloak:26.8.2")
          .withCommand("start-dev", "--import-realm")
          .withEnv("ADMIN_GATEWAY_CLIENT_SECRET", SECRET)
          .withEnv("CUSTOMER_GATEWAY_CLIENT_SECRET", SECRET)
          .withEnv("SEARCH_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("RESERVATION_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("INVENTORY_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("CACHE_UPDATER_CLIENT_SECRET", SECRET)
          .withEnv("PRICING_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("ANCILLARY_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("INSURANCE_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("DISCOUNT_SERVICE_CLIENT_SECRET", SECRET)
          .withEnv("KEYCLOAK_TEST_USER_PASSWORD", PASSWORD)
          .withEnv("ADMIN_UI_URL", GATEWAY)
          .withCopyFileToContainer(
              MountableFile.forHostPath(Path.of("../../infra/keycloak/car-rental-realm.json")),
              "/opt/keycloak/data/import/car-rental-realm.json")
          .withExposedPorts(8080)
          .waitingFor(
              Wait.forHttp("/realms/car-rental/.well-known/openid-configuration")
                  .forPort(8080)
                  .withStartupTimeout(java.time.Duration.ofMinutes(3)));

  static final ServiceStub inventory = new ServiceStub();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("server.port", () -> PORT);
    registry.add(
        "gateway.keycloak.public-url", () -> "http://localhost:" + keycloak.getMappedPort(8080));
    registry.add(
        "gateway.keycloak.internal-url", () -> "http://localhost:" + keycloak.getMappedPort(8080));
    registry.add("gateway.keycloak.client-secret", () -> SECRET);
    registry.add("gateway.services.inventory", () -> inventory.uri().toString());
    registry.add("gateway.services.administration", () -> inventory.uri().toString());
  }

  @AfterAll
  static void stopStub() {
    inventory.close();
  }

  final CookieManager cookies = new CookieManager();
  final HttpClient browser =
      HttpClient.newBuilder()
          .cookieHandler(new LocalhostCookies(cookies))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  final JsonMapper json = JsonMapper.builder().build();

  @Test
  void aCountryAdministratorSignsInWorksInTheirCountryAndSignsOut() throws Exception {
    HttpResponse<String> start = get(GATEWAY + "/oauth2/authorization/keycloak");
    assertThat(start.statusCode()).isEqualTo(302);
    String loginPage = location(start);
    assertThat(loginPage).startsWith("http://localhost:" + keycloak.getMappedPort(8080));

    HttpResponse<String> form = get(loginPage);
    assertThat(form.statusCode()).isEqualTo(200);
    assertThat(form.body()).as("the Google sign-in of the realm").contains("/broker/google/login");
    HttpResponse<String> submitted =
        postForm(formAction(form.body()), "username=admin-pl&password=" + encode(PASSWORD));
    assertThat(submitted.statusCode()).isEqualTo(302);
    String callback = location(submitted);
    assertThat(callback).startsWith(GATEWAY + "/login/oauth2/code/keycloak?");

    HttpResponse<String> signedIn = get(callback);
    assertThat(signedIn.statusCode()).isEqualTo(302);
    assertThat(location(signedIn)).isEqualTo(GATEWAY + "/");

    JsonNode session = json.readTree(get(GATEWAY + "/api/v1/session").body());
    assertThat(session.get("signedIn").asBoolean()).isTrue();
    assertThat(session.get("roles").get(0).asString()).isEqualTo("COUNTRY_ADMIN");
    assertThat(session.get("country").asString()).isEqualTo("pl");

    assertThat(get(GATEWAY + "/api/v1/admin/stock").statusCode()).isEqualTo(200);
    assertThat(inventory.last().header("X-Country")).isEqualTo("pl");
    String accessToken = inventory.last().header("Authorization").substring("Bearer ".length());
    JsonNode claims =
        json.readTree(java.util.Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
    assertThat(claims.get("iss").asString())
        .isEqualTo("http://localhost:" + keycloak.getMappedPort(8080) + "/realms/car-rental");
    assertThat(claims.get("country").asString()).isEqualTo("pl");
    assertThat(claims.get("roles").toString()).contains("country-admin");
    assertThat(claims.get("aud").toString()).contains("car-rental-api");

    HttpResponse<String> signedOut =
        browser.send(
            HttpRequest.newBuilder(URI.create(GATEWAY + "/logout"))
                .header("X-XSRF-TOKEN", cookie("ADMIN-XSRF-TOKEN"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(signedOut.statusCode()).isEqualTo(200);
    String keycloakLogout = json.readTree(signedOut.body()).get("redirectTo").asString();
    assertThat(keycloakLogout).contains("/realms/car-rental/protocol/openid-connect/logout?");
    assertThat(get(keycloakLogout).statusCode()).isLessThan(400);

    JsonNode after = json.readTree(get(GATEWAY + "/api/v1/session").body());
    assertThat(after.get("signedIn").asBoolean()).isFalse();
    assertThat(get(GATEWAY + "/api/v1/admin/stock").statusCode()).isEqualTo(401);
  }

  @Test
  void servicesAndTheCustomerGatewayGetTokensThatTheServicesAccept() {
    String keycloakUrl = "http://localhost:" + keycloak.getMappedPort(8080);
    var properties =
        new ServiceSecurityProperties(URI.create(keycloakUrl), null, null, null, null, null);
    JwtDecoder services = TokenValidation.decoder(properties);
    String tokenUri = keycloakUrl + "/realms/car-rental/protocol/openid-connect/token";

    for (String service :
        new String[] {
          "search-service",
          "reservation-service",
          "inventory-service",
          "cache-updater",
          "pricing-service",
          "ancillary-service",
          "insurance-service",
          "discount-service"
        }) {
      Jwt token = services.decode(new ServiceTokens(tokenUri, service, SECRET).token());
      assertThat(ServiceRoles.authorities(token))
          .as(service)
          .extracting(Object::toString)
          .containsExactly("ROLE_SERVICE");
    }

    Jwt guest = services.decode(new ServiceTokens(tokenUri, "customer-ui", SECRET).token());
    assertThat(ServiceRoles.authorities(guest)).as("the token for visitors has no role").isEmpty();
    assertThat(guest.getClaimAsString("azp")).isEqualTo("customer-ui");

    assertThatThrownBy(() -> new ServiceTokens(tokenUri, "admin-ui", SECRET).token())
        .as("the admin gateway has no token of its own");
    assertThatThrownBy(() -> new ServiceTokens(tokenUri, "search-service", "wrong").token());
  }

  @Test
  void aWrongPasswordIsRefusedByKeycloak() throws Exception {
    HttpResponse<String> form = get(location(get(GATEWAY + "/oauth2/authorization/keycloak")));
    HttpResponse<String> submitted =
        postForm(formAction(form.body()), "username=admin-pl&password=wrong");
    assertThat(submitted.statusCode()).isEqualTo(200);
    assertThat(json.readTree(get(GATEWAY + "/api/v1/session").body()).get("signedIn").asBoolean())
        .isFalse();
  }

  private HttpResponse<String> get(String url) throws IOException, InterruptedException {
    return browser.send(
        HttpRequest.newBuilder(URI.create(url)).GET().build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postForm(String url, String body)
      throws IOException, InterruptedException {
    return browser.send(
        HttpRequest.newBuilder(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String location(HttpResponse<?> response) {
    return response.headers().firstValue("Location").orElseThrow();
  }

  private static String formAction(String html) {
    Matcher matcher =
        Pattern.compile("id=\"kc-form-login\".*?action=\"([^\"]+)\"", Pattern.DOTALL).matcher(html);
    assertThat(matcher.find()).as("the Keycloak login form").isTrue();
    return matcher.group(1).replace("&amp;", "&");
  }

  private String cookie(String name) {
    return cookies.getCookieStore().getCookies().stream()
        .filter(c -> c.getName().equals(name))
        .map(HttpCookie::getValue)
        .findFirst()
        .orElseThrow();
  }

  /**
   * Browsers keep cookies marked Secure on http://localhost, as on HTTPS, and Keycloak relies on
   * that. Java's cookie store does not, so the flag is dropped here.
   */
  static final class LocalhostCookies extends java.net.CookieHandler {

    private final CookieManager store;

    LocalhostCookies(CookieManager store) {
      this.store = store;
    }

    @Override
    public java.util.Map<String, java.util.List<String>> get(
        URI uri, java.util.Map<String, java.util.List<String>> headers) throws IOException {
      return store.get(uri, headers);
    }

    @Override
    public void put(URI uri, java.util.Map<String, java.util.List<String>> headers)
        throws IOException {
      var withoutSecure = new java.util.HashMap<String, java.util.List<String>>();
      headers.forEach((name, values) ->
          withoutSecure.put(
              name,
              name != null && name.equalsIgnoreCase("Set-Cookie")
                  ? values.stream().map(v -> v.replaceAll("(?i);\\s*Secure", "")).toList()
                  : values));
      store.put(uri, withoutSecure);
    }
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
