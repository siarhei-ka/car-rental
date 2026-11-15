package psn.car.rental.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import psn.car.rental.shared.Problems;
import psn.car.rental.shared.security.ServiceTokens;

/**
 * Gives every call that the gateway passes on an access token. A signed-in user's call carries the
 * user's token, renewed when it has expired. A visitor's call carries the gateway's own token,
 * which has no role, so the services can tell that a valid caller sent it.
 */
@Component
class AccessTokens {

  private static final Logger log = LoggerFactory.getLogger(AccessTokens.class);

  private final OAuth2AuthorizedClientManager users;
  private final ServiceTokens gateway;

  AccessTokens(OAuth2AuthorizedClientManager users, GatewayProperties properties) {
    this.users = users;
    GatewayProperties.Keycloak keycloak = properties.keycloak();
    this.gateway =
        new ServiceTokens(
            KeycloakClientConfiguration.tokenUri(keycloak),
            keycloak.clientId(),
            keycloak.clientSecret());
  }

  HandlerFilterFunction<ServerResponse, ServerResponse> relay() {
    return (request, next) -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      String token;
      if (authentication instanceof OAuth2AuthenticationToken user) {
        token = userToken(user, request);
        if (token == null) {
          return problem(ProblemWriter.notSignedIn());
        }
      } else {
        try {
          token = gateway.token();
        } catch (RuntimeException e) {
          log.warn("The gateway got no token of its own from Keycloak: {}", e.getMessage());
          return problem(
              Problems.of(
                  HttpStatus.SERVICE_UNAVAILABLE,
                  "sign-in-unavailable",
                  "Sign-in service unavailable",
                  "The request cannot be passed on now. Please try again later."));
        }
      }
      String bearer = token;
      return next.handle(
          ServerRequest.from(request).headers(headers -> headers.setBearerAuth(bearer)).build());
    };
  }

  /** The user's token, or nothing when it has expired and cannot be renewed. */
  private String userToken(OAuth2AuthenticationToken user, ServerRequest request) {
    OAuth2AuthorizeRequest authorize =
        OAuth2AuthorizeRequest.withClientRegistrationId(user.getAuthorizedClientRegistrationId())
            .principal(user)
            .attribute(
                jakarta.servlet.http.HttpServletRequest.class.getName(), request.servletRequest())
            .build();
    try {
      OAuth2AuthorizedClient client = users.authorize(authorize);
      return client == null ? null : client.getAccessToken().getTokenValue();
    } catch (RuntimeException e) {
      log.info("The access token of a user could not be renewed: {}", e.getMessage());
      return null;
    }
  }

  private static ServerResponse problem(ProblemDetail problem) {
    return ServerResponse.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
