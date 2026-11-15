package psn.car.rental.gateway;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings of a gateway instance.
 *
 * @param keycloak how to reach Keycloak
 * @param csrfCookie the name of the cookie that carries the CSRF token to the UI
 * @param services the base address of each service, by service name
 * @param allowedOrigins the origins a browser may call this gateway from: the address of its UI
 *     (NFR-16). Any other origin is refused
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(
    Keycloak keycloak, String csrfCookie, Map<String, URI> services, List<String> allowedOrigins) {

  public GatewayProperties {
    allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
  }

  /**
   * @param publicUrl the address of Keycloak as the browser sees it. Tokens name it as the issuer
   * @param internalUrl the address the gateway uses for its own calls to Keycloak. In Docker it
   *     differs from the public one
   * @param realm the realm of the system
   * @param clientId the Keycloak client of this gateway
   * @param clientSecret the secret of that client
   */
  public record Keycloak(
      URI publicUrl, URI internalUrl, String realm, String clientId, String clientSecret) {}

  /** The base address of a service. Fails at startup when it is not configured. */
  public URI service(String name) {
    URI uri = services == null ? null : services.get(name);
    if (uri == null) {
      throw new IllegalStateException("gateway.services." + name + " is not configured");
    }
    return uri;
  }
}
