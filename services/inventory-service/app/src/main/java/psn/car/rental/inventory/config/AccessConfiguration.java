package psn.car.rental.inventory.config;

import static psn.car.rental.shared.security.ServiceRoles.COUNTRY_ADMIN;
import static psn.car.rental.shared.security.ServiceRoles.SERVICE;
import static psn.car.rental.shared.security.ServiceRoles.SUPER_ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/**
 * Who may call the inventory: the search and the reservation service for availability and holds,
 * administrators for stock and transfers.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers(HttpMethod.GET, "/api/v1/availability")
            .hasRole(SERVICE)
            .requestMatchers("/api/v1/holds/**")
            .hasRole(SERVICE)
            .requestMatchers("/api/v1/admin/**")
            .hasAnyRole(COUNTRY_ADMIN, SUPER_ADMIN);
  }
}
