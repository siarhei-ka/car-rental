package psn.car.rental.info.cartype;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import psn.car.rental.shared.security.AccessRules;

/** Car types and classes are open to every valid token, including the gateway's visitor token. */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return rules ->
        rules
            .requestMatchers(HttpMethod.GET, "/api/v1/car-types/**", "/api/v1/car-classes/**")
            .authenticated();
  }
}
