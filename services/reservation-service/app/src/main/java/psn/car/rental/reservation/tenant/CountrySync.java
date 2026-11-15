package psn.car.rental.reservation.tenant;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import psn.car.rental.reservation.config.ReservationProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Follows the list of countries of the administration service: loads it once at startup, then
 * applies the country events of the {@code reference-data} topic.
 */
@Component
@EnableScheduling
class CountrySync {

  private static final Logger log = LoggerFactory.getLogger(CountrySync.class);
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private final TenantMigrations migrations;
  private final JsonMapper json;
  private final RestClient administration;

  CountrySync(
      TenantMigrations migrations,
      JsonMapper json,
      RestClient.Builder builder,
      ReservationProperties properties) {
    this.migrations = migrations;
    this.json = json;
    if (properties.administrationUrl() == null) {
      this.administration = null;
    } else {
      JdkClientHttpRequestFactory factory =
          new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
      factory.setReadTimeout(TIMEOUT);
      this.administration =
          builder
              .baseUrl(properties.administrationUrl().toString())
              .requestFactory(factory)
              .build();
    }
  }

  /** Set once the country list of the administration service has been loaded. */
  private volatile boolean loaded;

  /**
   * Loads the country list at startup. If the administration service does not answer, for example
   * while it is still starting, {@link #retryUntilLoaded()} tries again. The events of the topic
   * describe only later changes, so the list must be loaded once.
   */
  @EventListener(ApplicationReadyEvent.class)
  void loadAtStartup() {
    load();
  }

  /** Tries again every few seconds until the country list has been loaded once. */
  @Scheduled(
      fixedDelayString = "${country-sync.retry-interval:PT10S}",
      initialDelayString = "${country-sync.retry-interval:PT10S}")
  void retryUntilLoaded() {
    if (!loaded) {
      load();
    }
  }

  @SuppressWarnings("unchecked")
  private void load() {
    if (administration == null) {
      loaded = true;
      return;
    }
    try {
      Map<String, Object> snapshot =
          administration.get().uri("/api/v1/reference-data").retrieve().body(Map.class);
      for (Object item : (List<Object>) snapshot.getOrDefault("countries", List.of())) {
        apply((Map<String, Object>) item);
      }
      loaded = true;
    } catch (RuntimeException e) {
      log.warn(
          "Countries could not be loaded from the administration service, trying again soon: {}",
          e.getMessage());
    }
  }

  @KafkaListener(
      topics = "${reference-data.topic:reference-data}",
      groupId = "${spring.application.name}")
  @SuppressWarnings("unchecked")
  void onEvent(String payload) {
    Map<String, Object> event;
    try {
      event = json.readValue(payload, Map.class);
    } catch (JacksonException e) {
      log.warn("Skipped a reference data event that is not valid JSON");
      return;
    }
    if ("COUNTRY".equals(event.get("kind")) && event.get("data") instanceof Map<?, ?> data) {
      apply((Map<String, Object>) data);
    }
  }

  private void apply(Map<String, Object> country) {
    Object code = country.get("code");
    if (!(code instanceof String text) || !text.matches("[a-z]{2}")) {
      log.warn("Skipped a country without a valid code: {}", code);
      return;
    }
    migrations.ensure(new Country(text), Boolean.TRUE.equals(country.get("active")));
  }
}
