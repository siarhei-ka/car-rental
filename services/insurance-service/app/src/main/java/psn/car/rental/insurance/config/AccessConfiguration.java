package psn.car.rental.insurance.config;

import static psn.car.rental.shared.security.ServiceRoles.SERVICE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/**
 * The options with their base prices are open to every valid token, including the gateway's token
 * for a visitor. The price for a driver is only for other services, which pass on the driver's age
 * and licence country.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers(HttpMethod.GET, "/api/v1/insurance-options")
            .authenticated()
            .requestMatchers(HttpMethod.POST, "/api/v1/insurance-quotes")
            .hasRole(SERVICE);
  }
}
