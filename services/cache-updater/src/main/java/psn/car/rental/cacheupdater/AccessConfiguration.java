package psn.car.rental.cacheupdater;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import psn.car.rental.shared.security.AccessRules;

/** The cache updater has no API: only its health check is open. */
@Configuration(proxyBeanMethods = false)
class AccessConfiguration {

  @Bean
  AccessRules accessRules() {
    return AccessRules.NONE;
  }
}
