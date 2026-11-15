package psn.car.rental.ancillary.config;

import static psn.car.rental.shared.security.ServiceRoles.COUNTRY_ADMIN;
import static psn.car.rental.shared.security.ServiceRoles.SUPER_ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/**
 * The extras on offer and the one-way fee are open to every valid token, including the gateway's
 * token for a visitor. Administrators of the country change them.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers(HttpMethod.GET, "/api/v1/extras", "/api/v1/one-way-fee")
            .authenticated()
            .requestMatchers("/api/v1/admin/**")
            .hasAnyRole(COUNTRY_ADMIN, SUPER_ADMIN);
  }
}
