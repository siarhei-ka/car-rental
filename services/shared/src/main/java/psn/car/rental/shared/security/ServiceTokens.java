package psn.car.rental.shared.security;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.http.OAuth2ErrorResponseErrorHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.http.converter.OAuth2AccessTokenResponseHttpMessageConverter;
import org.springframework.web.client.RestClient;

/**
 * The access token of a client of its own, obtained from Keycloak with the client credentials
 * grant. The token is kept in memory and renewed shortly before it expires. It belongs to the
 * client, not to the user of the current request, so it is the same for all requests.
 */
public final class ServiceTokens {

  private static final String REGISTRATION_ID = "service";
  private static final Duration TIMEOUT = Duration.ofSeconds(2);

  private final OAuth2AuthorizedClientManager manager;
  private final OAuth2AuthorizeRequest request;

  /**
   * @param tokenUri the token endpoint of the realm, at the address the caller reaches Keycloak
   */
  public ServiceTokens(String tokenUri, String clientId, String clientSecret) {
    ClientRegistration registration =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(tokenUri)
            .build();
    var registrations = new InMemoryClientRegistrationRepository(registration);
    var tokenClient = new RestClientClientCredentialsTokenResponseClient();
    tokenClient.setRestClient(keycloakClient());
    var clients =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            registrations, new InMemoryOAuth2AuthorizedClientService(registrations));
    clients.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials(grant -> grant.accessTokenResponseClient(tokenClient))
            .build());
    this.manager = clients;
    this.request =
        OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
            .principal(UsernamePasswordAuthenticationToken.unauthenticated(clientId, null))
            .build();
  }

  /**
   * The current access token.
   *
   * @throws org.springframework.security.oauth2.core.OAuth2AuthorizationException if Keycloak
   *     refuses the client
   * @throws RuntimeException if Keycloak cannot be reached in time
   */
  public String token() {
    OAuth2AuthorizedClient client = manager.authorize(request);
    if (client == null) {
      throw new IllegalStateException("Keycloak gave no token");
    }
    return client.getAccessToken().getTokenValue();
  }

  /** The client for the token endpoint, with the same time limit as calls between services. */
  private static RestClient keycloakClient() {
    JdkClientHttpRequestFactory factory =
        new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
    factory.setReadTimeout(TIMEOUT);
    return RestClient.builder()
        .requestFactory(factory)
        .configureMessageConverters(converters ->
            converters
                .addCustomConverter(new FormHttpMessageConverter())
                .addCustomConverter(new OAuth2AccessTokenResponseHttpMessageConverter()))
        .defaultStatusHandler(new OAuth2ErrorResponseErrorHandler())
        .build();
  }
}
