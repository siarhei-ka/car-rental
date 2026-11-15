package psn.car.rental.gateway;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/** Redis for the sessions of the gateway. */
@TestConfiguration(proxyBeanMethods = false)
class RedisContainer {

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redis() {
    return new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);
  }
}
