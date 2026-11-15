package psn.car.rental.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

@DisplayName("Validation of a token's claims")
class TokenValidationTest {

  private final ServiceSecurityProperties properties =
      new ServiceSecurityProperties(
          URI.create("http://keycloak:8080"),
          URI.create("http://localhost:8180/"),
          null,
          null,
          null,
          null);

  @Test
  void namesThePublicAddressAsIssuerAndTheInternalOneForKeys() {
    assertThat(properties.issuer()).isEqualTo("http://localhost:8180/realms/car-rental");
    assertThat(properties.endpoint("certs"))
        .isEqualTo("http://keycloak:8080/realms/car-rental/protocol/openid-connect/certs");
  }

  @Test
  void usesTheLocalKeycloakWhenNothingIsConfigured() {
    var defaults = new ServiceSecurityProperties(null, null, null, null, null, null);
    assertThat(defaults.issuer()).isEqualTo("http://localhost:8180/realms/car-rental");
    assertThat(defaults.audience()).isEqualTo("car-rental-api");
  }

  @Test
  void acceptsATokenOfTheRealmForTheSystem() {
    assertThat(valid(token("http://localhost:8180/realms/car-rental", List.of("car-rental-api"))))
        .isTrue();
  }

  @Test
  void refusesAnotherIssuer() {
    assertThat(valid(token("http://localhost:8180/realms/other", List.of("car-rental-api"))))
        .isFalse();
  }

  @Test
  void refusesATokenForAnotherAudience() {
    assertThat(valid(token("http://localhost:8180/realms/car-rental", List.of("account"))))
        .isFalse();
  }

  @Test
  void refusesAnExpiredToken() {
    Jwt expired =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .issuer("http://localhost:8180/realms/car-rental")
            .audience(List.of("car-rental-api"))
            .issuedAt(Instant.now().minusSeconds(3600))
            .expiresAt(Instant.now().minusSeconds(600))
            .build();
    assertThat(valid(expired)).isFalse();
  }

  @Test
  void mapsOnlyTheKnownRealmRoles() {
    Jwt token =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .claim("roles", List.of("country-admin", "offline_access", "service"))
            .build();
    assertThat(ServiceRoles.authorities(token))
        .extracting(Object::toString)
        .containsExactly("ROLE_COUNTRY_ADMIN", "ROLE_SERVICE");
  }

  private boolean valid(Jwt token) {
    return !TokenValidation.validator(properties).validate(token).hasErrors();
  }

  private static Jwt token(String issuer, List<String> audience) {
    return Jwt.withTokenValue("t")
        .header("alg", "RS256")
        .issuer(issuer)
        .audience(audience)
        .issuedAt(Instant.now().minusSeconds(10))
        .expiresAt(Instant.now().plusSeconds(300))
        .build();
  }
}
