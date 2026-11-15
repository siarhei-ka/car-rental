package psn.car.rental.cacheupdater;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;

@SpringBootTest(
    properties = {
      "cache-updater.administration-url=http://administration.invalid",
      "cache-updater.reload-check=PT1H",
      "logging.structured.format.console="
    })
@Import(CacheUpdaterTest.Infrastructure.class)
class CacheUpdaterTest {

  static final ReferenceCache.Snapshot SNAPSHOT =
      new ReferenceCache.Snapshot(
          List.of(Map.of("code", "pl", "name", "Poland", "currency", "PLN", "active", true)),
          List.of(Map.of("code", "SEDAN", "name", "Sedan", "active", true)),
          List.of(Map.of("code", "ECONOMY", "name", "Economy", "active", true)),
          List.of(
              Map.of(
                  "id", "waw-airport",
                  "country", "pl",
                  "name", "Warsaw Airport",
                  "city", "Warsaw",
                  "latitude", 52.17,
                  "longitude", 20.97,
                  "timeZone", "Europe/Warsaw",
                  "active", true)));

  @Autowired StringRedisTemplate redis;
  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired SnapshotLoader loader;
  @Autowired FakeSnapshotSource source;

  @BeforeEach
  void resetSource() {
    source.snapshot.set(SNAPSHOT);
  }

  @Test
  void loadsTheSnapshotAtStartup() {
    waitUntil(() -> redis.hasKey("ref:loaded"));

    assertThat(redis.opsForHash().get("ref:countries", "pl")).asString().contains("\"PLN\"");
    assertThat(redis.opsForHash().get("ref:locations:pl", "waw-airport"))
        .asString()
        .contains("Europe/Warsaw");
  }

  @Test
  void appliesEventsFromKafka() {
    waitUntil(() -> redis.hasKey("ref:loaded"));

    kafka.send(
        "reference-data",
        "location:de:ber-airport",
        """
        {"eventId":"e1","kind":"LOCATION","action":"SAVED","country":"de",
         "data":{"id":"ber-airport","country":"de","name":"Berlin Airport","active":true}}
        """);
    kafka.send("reference-data", "x", "not json");
    kafka.send(
        "reference-data",
        "car-type:VAN",
        """
        {"eventId":"e2","kind":"CAR_TYPE","action":"SAVED","data":{"code":"VAN","active":true}}
        """);

    waitUntil(() -> redis.opsForHash().hasKey("ref:car-types", "VAN"));
    assertThat(redis.opsForHash().get("ref:locations:de", "ber-airport"))
        .asString()
        .contains("Berlin Airport");
  }

  @Test
  void reloadsWhenRedisHasLostItsData() {
    waitUntil(() -> redis.hasKey("ref:loaded"));
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();

    boolean reloaded = loader.reloadIfEmpty();

    assertThat(reloaded).isTrue();
    assertThat(redis.opsForHash().hasKey("ref:car-classes", "ECONOMY")).isTrue();
    assertThat(loader.reloadIfEmpty()).isFalse();
  }

  @Test
  void aFailedLoadIsRetriedLater() {
    waitUntil(() -> redis.hasKey("ref:loaded"));
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    source.snapshot.set(null);

    assertThat(loader.reloadIfEmpty()).isFalse();
    assertThat(redis.hasKey("ref:loaded")).isFalse();

    source.snapshot.set(SNAPSHOT);
    assertThat(loader.reloadIfEmpty()).isTrue();
  }

  @Test
  void aFullLoadRemovesItemsThatNoLongerExist() {
    waitUntil(() -> redis.hasKey("ref:loaded"));
    redis.opsForHash().put("ref:locations:xx", "old", "{}");
    redis.opsForHash().put("ref:car-types", "OLD", "{}");
    redis.delete("ref:loaded");

    loader.reloadIfEmpty();

    assertThat(redis.hasKey("ref:locations:xx")).isFalse();
    assertThat(redis.opsForHash().hasKey("ref:car-types", "OLD")).isFalse();
    assertThat(redis.opsForHash().hasKey("ref:car-types", "SEDAN")).isTrue();
  }

  private static void waitUntil(BooleanSupplier condition) {
    long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
    while (!condition.getAsBoolean()) {
      if (System.nanoTime() > deadline) {
        throw new AssertionError("The condition was not met within 30 seconds");
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError(e);
      }
    }
  }

  /** Stands in for the administration service. A null snapshot makes the load fail. */
  static class FakeSnapshotSource implements SnapshotSource {

    final AtomicReference<ReferenceCache.Snapshot> snapshot = new AtomicReference<>(SNAPSHOT);

    @Override
    public ReferenceCache.Snapshot load() {
      ReferenceCache.Snapshot current = snapshot.get();
      if (current == null) {
        throw new IllegalStateException("The administration service is down");
      }
      return current;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Infrastructure {

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
      return new GenericContainer<>("redis:8.10-alpine").withExposedPorts(6379);
    }

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
      return new KafkaContainer("apache/kafka:4.2.2");
    }

    @Bean
    @Primary
    FakeSnapshotSource fakeSnapshotSource() {
      return new FakeSnapshotSource();
    }
  }
}
