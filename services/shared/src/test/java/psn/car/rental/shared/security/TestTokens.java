package psn.car.rental.shared.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Access tokens for MockMvc tests of a service. The tokens skip the signature check and carry the
 * claims that Keycloak would put in them; their roles go through the same mapping as real tokens.
 */
public final class TestTokens {

  private TestTokens() {}

  public static JwtRequestPostProcessor superAdmin() {
    return withRoles("super-admin");
  }

  public static JwtRequestPostProcessor countryAdmin(String country) {
    return token(List.of("country-admin"), country, "test-user");
  }

  public static JwtRequestPostProcessor customer() {
    return withRoles("customer");
  }

  /** A customer with a user ID of their own, for rules about whose data a request reaches. */
  public static JwtRequestPostProcessor customer(String userId) {
    return token(List.of("customer"), null, userId);
  }

  public static JwtRequestPostProcessor service() {
    return withRoles("service");
  }

  /** The token of the customer gateway for a visitor who has not signed in: no role at all. */
  public static JwtRequestPostProcessor guest() {
    return withRoles();
  }

  public static JwtRequestPostProcessor withRoles(String... realmRoles) {
    return token(List.of(realmRoles), null, "test-user");
  }

  private static JwtRequestPostProcessor token(List<String> roles, String country, String subject) {
    return jwt()
        .jwt(token -> {
          token.subject(subject).audience(List.of("car-rental-api")).claim("roles", roles);
          if (country != null) {
            token.claim("country", country);
          }
        })
        .authorities(ServiceRoles::authorities);
  }

  /**
   * Gives every MockMvc request a token with all roles, so tests of the business rules need no
   * token of their own. A test of the access rules sets its own token, or {@code anonymous()} for a
   * request without one.
   */
  @TestConfiguration(proxyBeanMethods = false)
  public static class AllRolesByDefault {

    @Bean
    MockMvcBuilderCustomizer allRolesByDefault() {
      return builder ->
          builder.defaultRequest(get("/").with(withRoles("super-admin", "customer", "service")));
    }
  }
}
