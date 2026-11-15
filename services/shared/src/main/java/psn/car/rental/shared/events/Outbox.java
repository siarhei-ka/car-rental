package psn.car.rental.shared.events;

/**
 * Events to send to Kafka, written in the database transaction of the change they describe. An
 * event is never lost and never describes a change that did not happen: if the transaction rolls
 * back, its events are gone too. {@link OutboxPublisher} sends them afterwards.
 */
public interface Outbox {

  /**
   * Adds an event to the outbox, in the caller's transaction.
   *
   * @param payload an object that is written as JSON
   */
  void add(String topic, String key, Object payload);
}
