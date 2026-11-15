package psn.car.rental.cacheupdater;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class InfrastructureConfig {

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }

  /** Retries a failed event every 5 seconds without limit, so an outage of Redis loses nothing. */
  @Bean
  DefaultErrorHandler kafkaErrorHandler() {
    return new DefaultErrorHandler(new FixedBackOff(5_000, FixedBackOff.UNLIMITED_ATTEMPTS));
  }
}
