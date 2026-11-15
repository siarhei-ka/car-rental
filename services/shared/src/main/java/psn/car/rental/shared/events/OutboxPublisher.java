package psn.car.rental.shared.events;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends the events of the outbox to Kafka in the order they were written, and marks them as
 * published. If Kafka is down, the events wait in the outbox and the business keeps working.
 */
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  private static final int BATCH = 100;

  private final JdbcClient jdbc;
  private final KafkaTemplate<String, String> kafka;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final String table;

  OutboxPublisher(
      JdbcClient jdbc,
      KafkaTemplate<String, String> kafka,
      TransactionTemplate transactions,
      Clock clock,
      String table) {
    this.jdbc = jdbc;
    this.kafka = kafka;
    this.transactions = transactions;
    this.clock = clock;
    this.table = table;
  }

  @Scheduled(fixedDelayString = "${car-rental.outbox.interval:PT0.5S}")
  void publishOnSchedule() {
    publish();
  }

  /**
   * Publishes the next batch.
   *
   * @return the number of events published
   */
  public int publish() {
    Integer published = transactions.execute(status -> publishBatch());
    return published == null ? 0 : published;
  }

  private int publishBatch() {
    // Locked rows are skipped, so that two instances never send the same event at the same time.
    List<Event> events =
        jdbc.sql(
                "select id, topic, event_key, payload::text as payload from "
                    + table
                    + " where published_at is null order by id limit :batch"
                    + " for update skip locked")
            .param("batch", BATCH)
            .query((rs, row) ->
                new Event(
                    rs.getLong("id"),
                    rs.getString("topic"),
                    rs.getString("event_key"),
                    rs.getString("payload")))
            .list();
    int published = 0;
    for (Event event : events) {
      try {
        kafka.send(event.topic(), event.key(), event.payload()).get(5, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.warn("Kafka is not reachable; {} events wait in the outbox", events.size() - published);
        if (e instanceof InterruptedException) {
          Thread.currentThread().interrupt();
        }
        break;
      }
      jdbc.sql("update " + table + " set published_at = :now where id = :id")
          .param("now", clock.instant().atOffset(ZoneOffset.UTC))
          .param("id", event.id())
          .update();
      published++;
    }
    return published;
  }

  private record Event(long id, String topic, String key, String payload) {}
}
