package psn.car.rental.shared.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import psn.car.rental.shared.security.ServiceRoles;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Analytics events")
class AnalyticsTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-12-01T08:00:00Z"), ZoneOffset.UTC);

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  static void signIn(String subject, String username, String... roles) {
    Jwt.Builder builder =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject)
            .claim("roles", List.of(roles))
            .claim("azp", "a-client");
    if (username != null) {
      builder.claim("preferred_username", username);
    }
    Jwt jwt = builder.build();
    SecurityContextHolder.getContext()
        .setAuthentication(new JwtAuthenticationToken(jwt, ServiceRoles.authorities(jwt)));
  }

  @Nested
  @DisplayName("Actor")
  class Actors {

    @Test
    void aCustomerIsNamedByTheirUserId() {
      signIn("user-1", "anna", "customer");
      assertThat(Actor.current()).isEqualTo(new Actor("CUSTOMER", "user-1"));
    }

    @Test
    void anAdministratorIsNamedByTheirUsername() {
      signIn("user-2", "admin-pl", "country-admin");
      assertThat(Actor.current()).isEqualTo(new Actor("ADMINISTRATOR", "admin-pl"));
      signIn("user-3", null, "super-admin");
      assertThat(Actor.current()).isEqualTo(new Actor("ADMINISTRATOR", "user-3"));
    }

    @Test
    void aServiceIsNamedByItsClient() {
      signIn("service-account", null, "service");
      assertThat(Actor.current()).isEqualTo(new Actor("SERVICE", "a-client"));
    }

    @Test
    void anyoneElseIsAVisitor() {
      assertThat(Actor.current()).isEqualTo(Actor.VISITOR);
      signIn("gateway", null);
      assertThat(Actor.current()).isEqualTo(Actor.VISITOR);
    }
  }

  @Test
  void theOutboxGetsTheWholeEnvelopeKeyedByCountry() {
    List<Object[]> added = new ArrayList<>();
    Outbox outbox = (topic, key, payload) -> added.add(new Object[] {topic, key, payload});
    signIn("user-1", "anna", "customer");

    new OutboxAnalytics(outbox, CLOCK, "a-service").record("x.happened", "pl", Map.of("n", 1));
    new OutboxAnalytics(outbox, CLOCK, "a-service").record("y.happened", null, Actor.VISITOR, null);

    assertThat(added.get(0)[0]).isEqualTo("analytics");
    assertThat(added.get(0)[1]).isEqualTo("pl");
    AnalyticsEvent event = (AnalyticsEvent) added.get(0)[2];
    assertThat(event.type()).isEqualTo("x.happened");
    assertThat(event.country()).isEqualTo("pl");
    assertThat(event.occurredAt()).isEqualTo(CLOCK.instant());
    assertThat(event.service()).isEqualTo("a-service");
    assertThat(event.actor()).isEqualTo(new Actor("CUSTOMER", "user-1"));
    assertThat(event.data()).containsEntry("n", 1);
    assertThat(event.eventId()).isNotNull();
    assertThat(added.get(1)[1]).isEqualTo("all");
    assertThat(((AnalyticsEvent) added.get(1)[2]).data()).isEmpty();
  }

  @Test
  @SuppressWarnings("unchecked")
  void aServiceWithoutADatabaseSendsTheEventAsJsonWithoutWaiting() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka is down")));

    new DirectAnalytics(kafka, JsonMapper.builder().build(), CLOCK, "search-service", Runnable::run)
        .record("search.performed", "de", Actor.VISITOR, Map.of("offers", 3));

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(kafka).send(eq("analytics"), eq("de"), payload.capture());
    assertThat(payload.getValue())
        .contains("\"type\":\"search.performed\"")
        .contains("\"service\":\"search-service\"")
        .contains("\"kind\":\"VISITOR\"")
        .contains("\"offers\":3");
  }

  @Test
  @SuppressWarnings("unchecked")
  void aFailedSendNeverReachesTheCaller() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(kafka.send(anyString(), anyString(), anyString()))
        .thenThrow(new IllegalStateException("no metadata"));

    new DirectAnalytics(kafka, JsonMapper.builder().build(), CLOCK, "search-service", Runnable::run)
        .record("search.performed", "de", Actor.VISITOR, Map.of());
  }
}
