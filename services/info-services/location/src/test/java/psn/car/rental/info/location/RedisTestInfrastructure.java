package psn.car.rental.info.location;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;

/** A real Redis in a container. */
@TestConfiguration(proxyBeanMethods = false)
class RedisTestInfrastructure {

  @Bean
  @ServiceConnection(name = "redis")
  GenericContainer<?> redis() {
    return new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);
  }
}
