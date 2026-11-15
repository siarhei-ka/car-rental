package psn.car.rental.shared.events;

import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import psn.car.rental.shared.security.ServiceRoles;

/**
 * Who caused an event, by ID only: never a name, an email address or a date of birth.
 *
 * @param kind CUSTOMER, ADMINISTRATOR, SERVICE or VISITOR
 * @param id the user ID in Keycloak for a customer, the username for an administrator, the client
 *     for a service, or null for a visitor
 */
public record Actor(String kind, String id) {

  public static final Actor VISITOR = new Actor("VISITOR", null);

  /** The caller of the current request, from its access token. */
  public static Actor current() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!(authentication instanceof JwtAuthenticationToken token)) {
      return VISITOR;
    }
    Jwt jwt = token.getToken();
    if (has(authentication, ServiceRoles.SUPER_ADMIN)
        || has(authentication, ServiceRoles.COUNTRY_ADMIN)) {
      return new Actor(
          "ADMINISTRATOR",
          Optional.ofNullable(jwt.getClaimAsString("preferred_username")).orElse(jwt.getSubject()));
    }
    if (has(authentication, ServiceRoles.CUSTOMER)) {
      return customer(jwt.getSubject());
    }
    if (has(authentication, ServiceRoles.SERVICE)) {
      return new Actor("SERVICE", jwt.getClaimAsString("azp"));
    }
    return VISITOR;
  }

  public static Actor customer(String customerId) {
    return new Actor("CUSTOMER", customerId);
  }

  private static boolean has(Authentication authentication, String role) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
  }
}
