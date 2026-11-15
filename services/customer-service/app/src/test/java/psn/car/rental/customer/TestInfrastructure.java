package psn.car.rental.customer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** A real PostgreSQL in a container and a fixed clock. */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

  /** The day of every test: the tests enter dates of birth relative to it. */
  public static final Instant NOW = Instant.parse("2026-11-11T10:00:00Z");

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18");
  }

  @Bean
  @Primary
  Clock testClock() {
    return Clock.fixed(NOW, ZoneOffset.UTC);
  }
}
