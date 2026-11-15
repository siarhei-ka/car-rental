package psn.car.rental.shared.security;

import java.util.*;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * The roles of the system. Tokens carry the Keycloak realm roles in the claim {@code roles}, and a
 * country administrator's country in the claim {@code country}.
 */
public final class ServiceRoles {

  public static final String SUPER_ADMIN = "SUPER_ADMIN";
  public static final String COUNTRY_ADMIN = "COUNTRY_ADMIN";
  public static final String CUSTOMER = "CUSTOMER";

  /** A service of the system calling another service with its own token. */
  public static final String SERVICE = "SERVICE";

  private static final Map<String, String> REALM_ROLES =
      Map.of(
          "super-admin", SUPER_ADMIN,
          "country-admin", COUNTRY_ADMIN,
          "customer", CUSTOMER,
          "service", SERVICE);

  private ServiceRoles() {}

  /** The authorities of a token: one {@code ROLE_} authority for each known realm role. */
  public static Collection<GrantedAuthority> authorities(Jwt token) {
    List<String> roles = token.getClaimAsStringList("roles");
    if (roles == null) {
      return List.of();
    }
    return roles.stream()
        .map(REALM_ROLES::get)
        .filter(Objects::nonNull)
        .distinct()
        .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
        .toList();
  }

  static Converter<Jwt, JwtAuthenticationToken> converter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(ServiceRoles::authorities);
    return jwt -> (JwtAuthenticationToken) converter.convert(jwt);
  }

  static boolean has(Authentication authentication, String role) {
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> ("ROLE_" + role).equals(authority.getAuthority()));
  }

  /** The country of a country administrator, from the token. */
  static Optional<String> country(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken token) {
      return Optional.ofNullable(token.getToken().getClaimAsString("country"));
    }
    return Optional.empty();
  }
}
