package psn.car.rental.gateway;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * The roles of the system. They are Keycloak realm roles, which a token lists in its {@code roles}
 * claim. A country administrator's token also names the country in its {@code country} claim.
 */
public final class Roles {

  public static final String SUPER_ADMIN = "SUPER_ADMIN";
  public static final String COUNTRY_ADMIN = "COUNTRY_ADMIN";
  public static final String CUSTOMER = "CUSTOMER";

  static final String ROLES_CLAIM = "roles";
  static final String COUNTRY_CLAIM = "country";

  private static final Set<String> KNOWN = Set.of(SUPER_ADMIN, COUNTRY_ADMIN, CUSTOMER);

  private Roles() {}

  /**
   * Adds an authority {@code ROLE_<ROLE>} for each known role in the token, for example {@code
   * ROLE_SUPER_ADMIN} for the Keycloak role {@code super-admin}. Other Keycloak roles are ignored.
   */
  static GrantedAuthoritiesMapper mapper() {
    return authorities -> {
      Set<GrantedAuthority> mapped = new HashSet<>(authorities);
      for (GrantedAuthority authority : authorities) {
        if (authority instanceof OidcUserAuthority oidc) {
          Object roles = oidc.getAttributes().get(ROLES_CLAIM);
          if (roles instanceof Collection<?> names) {
            for (Object name : names) {
              role(String.valueOf(name))
                  .ifPresent(role -> mapped.add(new SimpleGrantedAuthority("ROLE_" + role)));
            }
          }
        }
      }
      return mapped;
    };
  }

  /** The role for a Keycloak role name, if it is one of the system's roles. */
  static Optional<String> role(String keycloakName) {
    String role = keycloakName.toUpperCase(Locale.ROOT).replace('-', '_');
    return KNOWN.contains(role) ? Optional.of(role) : Optional.empty();
  }

  /** The system's roles of a signed-in user, ordered by name. */
  public static List<String> of(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(name -> name.startsWith("ROLE_"))
        .map(name -> name.substring("ROLE_".length()))
        .filter(KNOWN::contains)
        .sorted()
        .toList();
  }

  public static boolean has(Authentication authentication, String role) {
    return of(authentication).contains(role);
  }

  /** The country of a country administrator, from the token. */
  public static Optional<String> country(Authentication authentication) {
    if (authentication.getPrincipal() instanceof OAuth2User user
        && user.getAttribute(COUNTRY_CLAIM) instanceof String country
        && !country.isBlank()) {
      return Optional.of(country.toLowerCase(Locale.ROOT));
    }
    return Optional.empty();
  }
}
