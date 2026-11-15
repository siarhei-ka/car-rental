package psn.car.rental.shared.events;

import java.util.Map;

/**
 * Records events for usage analytics. Analytics never blocks the business: a service with a
 * database writes the event to its outbox in the transaction of the change; a service without one
 * sends it to Kafka without waiting.
 */
public interface Analytics {

  /** Records an event caused by the caller of the current request. */
  default void record(String type, String country, Map<String, Object> data) {
    record(type, country, Actor.current(), data);
  }

  void record(String type, String country, Actor actor, Map<String, Object> data);
}
