package psn.car.rental.gateway;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;

/**
 * The gateway as an OAuth 2 client of Keycloak.
 *
 * <p>The client is described here rather than discovered from Keycloak, because the browser and the
 * gateway reach Keycloak at different addresses in Docker: the browser is sent to the public
 * address, and the gateway's own calls go to the internal one. Tokens name the public address as
 * their issuer.
 *
 * <p>The tokens of a user are kept in the user's session, which is stored in Redis, so any instance
 * of the gateway can serve the user.
 */
@Configuration(proxyBeanMethods = false)
class KeycloakClientConfiguration {

  static final String REGISTRATION_ID = "keycloak";

  @Bean
  ClientRegistrationRepository clientRegistrationRepository(GatewayProperties properties) {
    return new InMemoryClientRegistrationRepository(registration(properties.keycloak()));
  }

  static ClientRegistration registration(GatewayProperties.Keycloak keycloak) {
    String issuer = realmUrl(keycloak.publicUrl().toString(), keycloak.realm());
    String internal = realmUrl(keycloak.internalUrl().toString(), keycloak.realm());
    String endpoints = "/protocol/openid-connect";
    return ClientRegistration.withRegistrationId(REGISTRATION_ID)
        .clientId(keycloak.clientId())
        .clientSecret(keycloak.clientSecret())
        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
        .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
        .scope("openid", "profile")
        .issuerUri(issuer)
        .authorizationUri(issuer + endpoints + "/auth")
        .tokenUri(internal + endpoints + "/token")
        .jwkSetUri(internal + endpoints + "/certs")
        .userInfoUri(internal + endpoints + "/userinfo")
        .userNameAttributeName(IdTokenClaimNames.SUB)
        .providerConfigurationMetadata(
            Map.of("end_session_endpoint", issuer + endpoints + "/logout"))
        .clientName("Keycloak")
        .build();
  }

  static String tokenUri(GatewayProperties.Keycloak keycloak) {
    return realmUrl(keycloak.internalUrl().toString(), keycloak.realm())
        + "/protocol/openid-connect/token";
  }

  private static String realmUrl(String base, String realm) {
    return base.replaceAll("/+$", "") + "/realms/" + realm;
  }

  @Bean
  OAuth2AuthorizedClientRepository authorizedClientRepository() {
    return new HttpSessionOAuth2AuthorizedClientRepository();
  }

  /** Used when a call is passed on: it renews an expired access token with the refresh token. */
  @Bean
  OAuth2AuthorizedClientManager authorizedClientManager(
      ClientRegistrationRepository registrations, OAuth2AuthorizedClientRepository clients) {
    var manager = new DefaultOAuth2AuthorizedClientManager(registrations, clients);
    manager.setAuthorizedClientProvider(
        OAuth2AuthorizedClientProviderBuilder.builder().authorizationCode().refreshToken().build());
    return manager;
  }
}
