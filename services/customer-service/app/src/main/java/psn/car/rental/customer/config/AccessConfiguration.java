package psn.car.rental.customer.config;

import static psn.car.rental.shared.security.ServiceRoles.CUSTOMER;
import static psn.car.rental.shared.security.ServiceRoles.SERVICE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/**
 * Only a customer reaches the profile, and only their own: it follows from the token. Other
 * services read any customer's profile by ID.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers("/api/v1/profile")
            .hasRole(CUSTOMER)
            .requestMatchers(HttpMethod.GET, "/api/v1/profiles/*")
            .hasRole(SERVICE);
  }
}
