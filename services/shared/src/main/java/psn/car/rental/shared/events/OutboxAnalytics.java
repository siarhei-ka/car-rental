package psn.car.rental.shared.events;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/** Writes analytics events to the outbox, in the caller's transaction. */
class OutboxAnalytics implements Analytics {

  private final Outbox outbox;
  private final Clock clock;
  private final String service;

  OutboxAnalytics(Outbox outbox, Clock clock, String service) {
    this.outbox = outbox;
    this.clock = clock;
    this.service = service;
  }

  @Override
  public void record(String type, String country, Actor actor, Map<String, Object> data) {
    AnalyticsEvent event =
        new AnalyticsEvent(UUID.randomUUID(), type, country, clock.instant(), service, actor, data);
    outbox.add(AnalyticsEvent.TOPIC, event.key(), event);
  }
}
