package psn.car.rental.shared.security;

import java.util.List;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * When a service accepts a token: Keycloak signed it with a key of the realm, it has not expired,
 * it names the realm at its public address as issuer, and it names the system as its audience.
 */
public final class TokenValidation {

  private TokenValidation() {}

  /** A decoder that loads the keys of the realm from Keycloak's internal address. */
  public static JwtDecoder decoder(ServiceSecurityProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.endpoint("certs")).build();
    decoder.setJwtValidator(validator(properties));
    return decoder;
  }

  static OAuth2TokenValidator<Jwt> validator(ServiceSecurityProperties properties) {
    OAuth2TokenValidator<Jwt> audience =
        token -> {
          List<String> audiences = token.getAudience();
          if (audiences != null && audiences.contains(properties.audience())) {
            return OAuth2TokenValidatorResult.success();
          }
          return OAuth2TokenValidatorResult.failure(
              new OAuth2Error(
                  "invalid_token", "The token is not meant for " + properties.audience(), null));
        };
    return new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(properties.issuer()), audience);
  }
}
