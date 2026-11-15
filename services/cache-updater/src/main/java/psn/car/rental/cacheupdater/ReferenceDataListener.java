package psn.car.rental.cacheupdater;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Applies reference data events to Redis. If Redis fails, the error handler retries the event until
 * it succeeds, so no event is skipped. An event that is not valid JSON is skipped.
 */
@Component
class ReferenceDataListener {

  private static final Logger log = LoggerFactory.getLogger(ReferenceDataListener.class);

  private final ReferenceCache cache;
  private final JsonMapper json;

  ReferenceDataListener(ReferenceCache cache, JsonMapper json) {
    this.cache = cache;
    this.json = json;
  }

  @KafkaListener(topics = "${cache-updater.topic:reference-data}", groupId = "cache-updater")
  @SuppressWarnings("unchecked")
  void onEvent(String payload) {
    Map<String, Object> event;
    try {
      event = json.readValue(payload, Map.class);
    } catch (JacksonException e) {
      log.warn("Skipped a reference data event that is not valid JSON");
      return;
    }
    cache.apply(event);
  }
}
