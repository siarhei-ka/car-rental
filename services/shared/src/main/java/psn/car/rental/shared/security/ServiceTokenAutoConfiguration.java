package psn.car.rental.shared.security;

import java.io.IOException;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * A service that calls other services sends its own access token in every call, see {@link
 * ServiceTokens}. It is turned on by the property {@code car-rental.security.client-id}.
 */
@AutoConfiguration
@ConditionalOnClass({OAuth2AuthorizedClientManager.class, RestClientCustomizer.class})
@ConditionalOnProperty("car-rental.security.client-id")
@EnableConfigurationProperties(ServiceSecurityProperties.class)
public class ServiceTokenAutoConfiguration {

  @Bean
  RestClientCustomizer serviceTokenRestClientCustomizer(ServiceSecurityProperties properties) {
    ClientHttpRequestInterceptor tokens = interceptor(properties);
    return builder -> builder.requestInterceptor(tokens);
  }

  static ClientHttpRequestInterceptor interceptor(ServiceSecurityProperties properties) {
    ServiceTokens tokens =
        new ServiceTokens(
            properties.endpoint("token"), properties.clientId(), properties.clientSecret());
    return (request, body, execution) -> {
      String token;
      try {
        token = tokens.token();
      } catch (RuntimeException e) {
        // A missing token counts as an unreachable dependency, like a timeout.
        throw new IOException("No service token from Keycloak: " + e.getMessage(), e);
      }
      request.getHeaders().setBearerAuth(token);
      return execution.execute(request, body);
    };
  }
}
