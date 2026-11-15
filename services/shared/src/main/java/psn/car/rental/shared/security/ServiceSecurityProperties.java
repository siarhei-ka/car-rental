package psn.car.rental.shared.security;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * How a service checks tokens and obtains its own.
 *
 * @param keycloakUrl the address at which the service reaches Keycloak, used for its keys and for
 *     its own tokens
 * @param keycloakPublicUrl the address at which browsers reach Keycloak; tokens name it as their
 *     issuer
 * @param realm the Keycloak realm
 * @param audience the audience every accepted token must name
 * @param clientId the Keycloak client of this service, only needed by a service that calls other
 *     services
 * @param clientSecret the secret of that client
 */
@ConfigurationProperties("car-rental.security")
public record ServiceSecurityProperties(
    URI keycloakUrl,
    URI keycloakPublicUrl,
    String realm,
    String audience,
    String clientId,
    String clientSecret) {

  private static final URI LOCAL_KEYCLOAK = URI.create("http://localhost:8180");

  public ServiceSecurityProperties {
    keycloakUrl = keycloakUrl == null ? LOCAL_KEYCLOAK : keycloakUrl;
    keycloakPublicUrl = keycloakPublicUrl == null ? keycloakUrl : keycloakPublicUrl;
    realm = realm == null ? "car-rental" : realm;
    audience = audience == null ? "car-rental-api" : audience;
  }

  /** The issuer that every accepted token names. */
  public String issuer() {
    return realmUrl(keycloakPublicUrl);
  }

  String endpoint(String name) {
    return realmUrl(keycloakUrl) + "/protocol/openid-connect/" + name;
  }

  private String realmUrl(URI base) {
    return base.toString().replaceAll("/+$", "") + "/realms/" + realm;
  }
}
