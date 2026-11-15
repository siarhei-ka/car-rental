package psn.car.rental.shared.events;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Sends analytics events straight to Kafka, for a service without a database. The send runs on its
 * own thread, so the request never waits for Kafka; an event that Kafka does not take is lost and
 * logged. It is used only for events that describe no change, such as a search.
 */
class DirectAnalytics implements Analytics {

  private static final Logger log = LoggerFactory.getLogger(DirectAnalytics.class);

  private final KafkaTemplate<String, String> kafka;
  private final JsonMapper json;
  private final Clock clock;
  private final String service;
  private final Executor executor;

  DirectAnalytics(
      KafkaTemplate<String, String> kafka,
      JsonMapper json,
      Clock clock,
      String service,
      Executor executor) {
    this.kafka = kafka;
    this.json = json;
    this.clock = clock;
    this.service = service;
    this.executor = executor;
  }

  @Override
  public void record(String type, String country, Actor actor, Map<String, Object> data) {
    AnalyticsEvent event =
        new AnalyticsEvent(UUID.randomUUID(), type, country, clock.instant(), service, actor, data);
    String payload = json.writeValueAsString(event);
    executor.execute(() -> {
      try {
        kafka
            .send(AnalyticsEvent.TOPIC, event.key(), payload)
            .whenComplete((result, error) -> {
              if (error != null) {
                log.warn("An analytics event {} was not sent: {}", type, error.getMessage());
              }
            });
      } catch (RuntimeException e) {
        log.warn("An analytics event {} was not sent: {}", type, e.getMessage());
      }
    });
  }
}
