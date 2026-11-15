package psn.car.rental.gateway;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the UI who is signed in. The UI calls it first, and the call also sets the CSRF cookie that
 * the UI needs for requests that change data.
 */
@RestController
class SessionController {

  /**
   * @param signedIn whether a user is signed in
   * @param name the name to show, when signed in
   * @param roles the system's roles of the user
   * @param country the country of a country administrator
   */
  record Session(boolean signedIn, String name, List<String> roles, String country) {}

  @GetMapping("/api/v1/session")
  Session session(Authentication authentication, CsrfToken csrf) {
    csrf.getToken();
    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser user)) {
      return new Session(false, null, List.of(), null);
    }
    return new Session(
        true,
        displayName(user),
        Roles.of(authentication),
        Roles.country(authentication).orElse(null));
  }

  private static String displayName(OidcUser user) {
    if (user.getFullName() != null && !user.getFullName().isBlank()) {
      return user.getFullName();
    }
    if (user.getPreferredUsername() != null) {
      return user.getPreferredUsername();
    }
    return user.getSubject();
  }
}
