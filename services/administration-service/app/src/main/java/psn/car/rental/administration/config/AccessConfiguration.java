package psn.car.rental.administration.config;

import static psn.car.rental.shared.security.ServiceRoles.COUNTRY_ADMIN;
import static psn.car.rental.shared.security.ServiceRoles.SERVICE;
import static psn.car.rental.shared.security.ServiceRoles.SUPER_ADMIN;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/**
 * Who may call the administration service. The snapshot of all reference data is for other
 * services. A country administrator manages the locations of the country and reads the shared data,
 * and only a super administrator changes the shared data.
 */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers(HttpMethod.GET, "/api/v1/reference-data")
            .hasRole(SERVICE)
            .requestMatchers("/api/v1/admin/locations/**")
            .hasAnyRole(COUNTRY_ADMIN, SUPER_ADMIN)
            .requestMatchers(HttpMethod.GET, "/api/v1/admin/**")
            .hasAnyRole(COUNTRY_ADMIN, SUPER_ADMIN)
            .requestMatchers("/api/v1/admin/**")
            .hasRole(SUPER_ADMIN);
  }
}
