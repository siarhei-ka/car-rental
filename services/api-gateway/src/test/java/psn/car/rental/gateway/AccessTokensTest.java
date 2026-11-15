package psn.car.rental.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class AccessTokensTest {

  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void answers503WhenKeycloakGivesTheGatewayNoToken() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(new TestingAuthenticationToken("anonymous", null));
    AccessTokens tokens = new AccessTokens(request -> null, properties("http://localhost:1"));

    ServerResponse response = tokens.relay().filter(request(), next -> ServerResponse.ok().build());

    assertThat(response.statusCode().value()).isEqualTo(503);
  }

  @Test
  void answers401WhenTheTokenOfAUserCannotBeRenewed() throws Exception {
    var user = new DefaultOAuth2User(AuthorityUtils.NO_AUTHORITIES, Map.of("sub", "user-1"), "sub");
    SecurityContextHolder.getContext()
        .setAuthentication(new OAuth2AuthenticationToken(user, List.of(), "keycloak"));
    AccessTokens tokens = new AccessTokens(request -> null, properties("http://localhost:1"));

    ServerResponse response = tokens.relay().filter(request(), next -> ServerResponse.ok().build());

    assertThat(response.statusCode().value()).isEqualTo(401);
  }

  private static GatewayProperties properties(String keycloak) {
    return new GatewayProperties(
        new GatewayProperties.Keycloak(
            URI.create(keycloak), URI.create(keycloak), "car-rental", "customer-ui", "secret"),
        "XSRF-TOKEN",
        Map.of(),
        List.of());
  }

  private static ServerRequest request() {
    return ServerRequest.create(
        new MockHttpServletRequest("GET", "/api/v1/locations"),
        List.of(new StringHttpMessageConverter()));
  }
}
