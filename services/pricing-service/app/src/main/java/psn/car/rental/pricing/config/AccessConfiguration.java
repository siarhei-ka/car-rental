package psn.car.rental.pricing.config;

import static psn.car.rental.shared.security.ServiceRoles.COUNTRY_ADMIN;
import static psn.car.rental.shared.security.ServiceRoles.CUSTOMER;
import static psn.car.rental.shared.security.ServiceRoles.SERVICE;
import static psn.car.rental.shared.security.ServiceRoles.SUPER_ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/**
 * List prices are open to every valid token, including the gateway's token for a visitor and the
 * search service's token. The final price is only for a signed-in customer, because it depends on
 * the driver, and for other services that act for a customer. Administrators of the country change
 * the rates.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers(HttpMethod.GET, "/api/v1/rates")
            .authenticated()
            .requestMatchers(HttpMethod.POST, "/api/v1/quotes")
            .hasRole(CUSTOMER)
            .requestMatchers(HttpMethod.POST, "/api/v1/customers/*/quotes")
            .hasRole(SERVICE)
            .requestMatchers("/api/v1/admin/**")
            .hasAnyRole(COUNTRY_ADMIN, SUPER_ADMIN);
  }
}
