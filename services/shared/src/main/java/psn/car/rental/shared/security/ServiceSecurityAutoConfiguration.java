package psn.car.rental.shared.security;

import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Every service accepts only requests with a valid access token of the Keycloak realm, and checks
 * the roles and the country in it.
 *
 * <p>A token is valid when Keycloak signed it, it has not expired, it names the realm as its issuer
 * and the system as its audience. A service turns it on by declaring its {@link AccessRules}.
 * Sessions, cookies and CSRF tokens play no part, because callers send the token in every request.
 */
@AutoConfiguration(
    beforeName = {
      "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
      "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration",
      "org.springframework.boot.security.oauth2.server.resource.autoconfigure.web.OAuth2ResourceServerWebSecurityAutoConfiguration"
    })
@ConditionalOnClass({HttpSecurity.class, JwtDecoder.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(AccessRules.class)
@EnableConfigurationProperties(ServiceSecurityProperties.class)
public class ServiceSecurityAutoConfiguration {

  /** Paths open to everyone: health checks for Docker, the API description and error pages. */
  static final String[] PUBLIC_PATHS = {
    "/actuator/health", "/actuator/health/**", "/actuator/info", "/openapi.yaml", "/error"
  };

  @Bean
  JwtDecoder jwtDecoder(ServiceSecurityProperties properties) {
    return TokenValidation.decoder(properties);
  }

  @Bean
  SecurityFilterChain serviceSecurityFilterChain(HttpSecurity http, List<AccessRules> services) {
    http.authorizeHttpRequests(
            rules -> {
              rules.requestMatchers(PUBLIC_PATHS).permitAll();
              rules.requestMatchers("/actuator/**").hasRole(ServiceRoles.SERVICE);
              services.forEach(service -> service.apply(rules));
              rules.anyRequest().denyAll();
            })
        .oauth2ResourceServer(server ->
            server
                .jwt(jwt -> jwt.jwtAuthenticationConverter(ServiceRoles.converter()))
                .authenticationEntryPoint(
                    (request, response, failure) -> Refusals.notSignedIn(response))
                .accessDeniedHandler((request, response, denied) ->
                    Refusals.forbidden(response, "The token does not allow this request")))
        .exceptionHandling(exceptions ->
            exceptions
                .authenticationEntryPoint(
                    (request, response, failure) -> Refusals.notSignedIn(response))
                .accessDeniedHandler((request, response, denied) ->
                    Refusals.forbidden(response, "The token does not allow this request")))
        .sessionManagement(
            sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .requestCache(cache -> cache.disable())
        .httpBasic(basic -> basic.disable())
        .formLogin(form -> form.disable())
        .logout(logout -> logout.disable())
        .addFilterAfter(new CountryCheckFilter(), AuthorizationFilter.class);
    return http.build();
  }
}
