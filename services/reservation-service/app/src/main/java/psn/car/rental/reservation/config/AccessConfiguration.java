package psn.car.rental.reservation.config;

import static psn.car.rental.shared.security.ServiceRoles.COUNTRY_ADMIN;
import static psn.car.rental.shared.security.ServiceRoles.CUSTOMER;
import static psn.car.rental.shared.security.ServiceRoles.SUPER_ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import psn.car.rental.shared.security.AccessRules;

/** Who may call the reservation service: customers for reservations, administrators at the desk. */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers("/api/v1/reservations/**")
            .hasRole(CUSTOMER)
            .requestMatchers("/api/v1/admin/**")
            .hasAnyRole(COUNTRY_ADMIN, SUPER_ADMIN);
  }
}
