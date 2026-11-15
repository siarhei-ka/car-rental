package psn.car.rental.shared.events;

import java.time.Clock;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes events to the outbox table, in the transaction of the caller. The table name is
 * configuration, never input, so it is safe in SQL.
 */
class JdbcOutbox implements Outbox {

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock clock;
  private final String table;

  JdbcOutbox(JdbcClient jdbc, JsonMapper json, Clock clock, String table) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
    this.table = table;
  }

  @Override
  public void add(String topic, String key, Object payload) {
    jdbc.sql(
            "insert into "
                + table
                + " (topic, event_key, payload, created_at)"
                + " values (:topic, :key, cast(:payload as jsonb), :now)")
        .param("topic", topic)
        .param("key", key)
        .param("payload", json.writeValueAsString(payload))
        .param("now", clock.instant().atOffset(ZoneOffset.UTC))
        .update();
  }
}
