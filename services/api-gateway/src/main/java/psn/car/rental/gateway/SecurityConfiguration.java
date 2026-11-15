package psn.car.rental.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sign-in, sign-out, CSRF protection and the rules shared by both gateways. The routes of each UI
 * add their own rules through {@link AccessRules}.
 *
 * <ul>
 *   <li>The UI starts the sign-in by opening {@code /oauth2/authorization/keycloak}. After it the
 *       browser returns to the start page of the UI.
 *   <li>The UI signs out with {@code POST /logout}. The answer names the Keycloak address that ends
 *       the Keycloak session too, and the UI opens it.
 *   <li>Requests that change data must carry the CSRF token from the cookie in the {@code
 *       X-XSRF-TOKEN} header.
 *   <li>A request that is not allowed is answered with 401 or 403 in the error format of the
 *       services, never with a redirect, because only the UI's scripts call the API.
 *   <li>Browsers may call the gateway only from the origin of its own UI (NFR-16).
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      AccessRules rules,
      GatewayProperties properties,
      ProblemWriter problems,
      ClientRegistrationRepository registrations,
      OAuth2AuthorizedClientRepository authorizedClients,
      JsonMapper json) {
    CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
    csrfTokens.setCookieName(properties.csrfCookie());
    csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));

    http.authorizeHttpRequests(
            requests -> {
              requests
                  .requestMatchers(
                      "/api/v1/session", "/openapi.yaml", "/actuator/health/**", "/error")
                  .permitAll();
              rules.customize(requests);
              requests.anyRequest().denyAll();
            })
        .oauth2Login(login ->
            login
                .authorizedClientRepository(authorizedClients)
                .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(Roles.mapper()))
                .defaultSuccessUrl("/", true)
                .failureUrl("/?signInFailed"))
        .logout(logout -> logout.logoutSuccessHandler(signOut(registrations, json)))
        .cors(cors -> cors.configurationSource(cors(properties)))
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
        .exceptionHandling(errors ->
            errors
                .authenticationEntryPoint(
                    (request, response, e) -> problems.write(response, ProblemWriter.notSignedIn()))
                .accessDeniedHandler((request, response, e) ->
                    problems.write(
                        response,
                        ProblemWriter.forbidden(
                            "This account may not use this function, or the CSRF token"
                                + " is missing."))))
        .requestCache(cache -> cache.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable());
    return http.build();
  }

  /** Only the UI of this gateway may call it from a browser, with its session cookie. */
  static CorsConfigurationSource cors(GatewayProperties properties) {
    CorsConfiguration cors = new CorsConfiguration();
    cors.setAllowedOrigins(properties.allowedOrigins());
    cors.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    cors.setAllowedHeaders(
        List.of(
            "Content-Type", "X-Country", "X-XSRF-TOKEN", "Idempotency-Key", "X-Correlation-Id"));
    cors.setExposedHeaders(List.of("X-Correlation-Id", "Location"));
    cors.setAllowCredentials(true);
    cors.setMaxAge(3600L);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", cors);
    return source;
  }

  /** Sign-in requests carry a PKCE challenge, although the gateway is a confidential client. */
  static OAuth2AuthorizationRequestResolver withPkce(ClientRegistrationRepository registrations) {
    var resolver =
        new DefaultOAuth2AuthorizationRequestResolver(
            registrations,
            OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
    resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
    return resolver;
  }

  /**
   * Ends the gateway session and answers with the Keycloak address that ends the Keycloak session
   * and returns to the UI.
   */
  static LogoutSuccessHandler signOut(ClientRegistrationRepository registrations, JsonMapper json) {
    var keycloak =
        new OidcClientInitiatedLogoutSuccessHandler(registrations) {
          String target(
              HttpServletRequest request, HttpServletResponse response, Authentication user) {
            return determineTargetUrl(request, response, user);
          }
        };
    keycloak.setPostLogoutRedirectUri("{baseUrl}/");
    keycloak.setDefaultTargetUrl("/");
    return (request, response, authentication) -> {
      String target =
          authentication == null ? "/" : keycloak.target(request, response, authentication);
      writeJson(response, json, Map.of("redirectTo", target));
    };
  }

  private static void writeJson(HttpServletResponse response, JsonMapper json, Object body)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    json.writeValue(response.getOutputStream(), body);
  }

  /** The access rules of the routes of one UI. */
  @FunctionalInterface
  interface AccessRules
      extends Customizer<
          AuthorizeHttpRequestsConfigurer<HttpSecurity>
              .AuthorizationManagerRequestMatcherRegistry> {}
}
