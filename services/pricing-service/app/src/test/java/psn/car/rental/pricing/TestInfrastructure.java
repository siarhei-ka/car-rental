package psn.car.rental.pricing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A real PostgreSQL and Kafka in containers, shared by all tests that use the same Spring context.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

  @Bean
  @ServiceConnection
  PostgreSQLContainer postgres() {
    return new PostgreSQLContainer("postgres:18");
  }

  @Bean
  @ServiceConnection
  KafkaContainer kafka() {
    return new KafkaContainer("apache/kafka:4.2.2");
  }

  /** Every service a quote depends on is the stub. */
  @Bean
  DynamicPropertyRegistrar dependencies() {
    return registry -> {
      for (String name :
          new String[] {"location-info", "customer", "insurance", "ancillary", "discount"}) {
        registry.add("pricing." + name + "-url", () -> DependencyStub.INSTANCE.uri().toString());
      }
    };
  }

  @Bean
  @Primary
  MutableClock testClock() {
    return new MutableClock(Instant.parse("2026-12-01T08:00:00Z"));
  }

  /** A clock that tests can move forward. */
  public static final class MutableClock extends Clock {

    private volatile Instant now;

    MutableClock(Instant start) {
      this.now = start;
    }

    public void advance(Duration duration) {
      now = now.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
