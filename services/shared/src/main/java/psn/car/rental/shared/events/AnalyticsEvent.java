package psn.car.rental.shared.events;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An event for usage analytics. Every event of every service has this envelope; what happened is in
 * {@code type} and {@code data}. Consumers tell a repeated delivery by {@code eventId}.
 *
 * @param type what happened, such as {@code reservation.created}
 * @param country the country it happened in, or null for data shared by all countries
 * @param data the details, which depend on the type
 */
public record AnalyticsEvent(
    UUID eventId,
    String type,
    String country,
    Instant occurredAt,
    String service,
    Actor actor,
    Map<String, Object> data) {

  /** The Kafka topic of all analytics events. */
  public static final String TOPIC = "analytics";

  public AnalyticsEvent {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(occurredAt, "occurredAt");
    Objects.requireNonNull(actor, "actor");
    data = data == null ? Map.of() : data;
  }

  /** The Kafka key: the country, so that the events of one country stay in order. */
  public String key() {
    return country == null ? "all" : country;
  }
}
