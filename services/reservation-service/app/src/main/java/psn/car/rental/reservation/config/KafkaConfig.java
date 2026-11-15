package psn.car.rental.reservation.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConfig {

  /**
   * Retries a failed event every 5 seconds without limit, for example while the database is down.
   */
  @Bean
  DefaultErrorHandler kafkaErrorHandler() {
    return new DefaultErrorHandler(new FixedBackOff(5_000, FixedBackOff.UNLIMITED_ATTEMPTS));
  }
}
