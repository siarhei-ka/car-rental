package psn.car.rental.administration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.kafka.KafkaContainer;
import psn.car.rental.shared.security.TestTokens;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    properties = {"administration.seed-data=true", "logging.structured.format.console="})
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, TestTokens.AllRolesByDefault.class})
@DisplayName("Administration API")
class AdministrationApiTest {

  @Autowired MockMvc mvc;
  @Autowired org.springframework.jdbc.core.simple.JdbcClient jdbc;
  @Autowired KafkaContainer kafka;
  @Autowired JsonMapper json;

  @Nested
  @DisplayName("Reference data")
  class ReferenceData {

    @Test
    void startsWithTheExampleData() throws Exception {
      mvc.perform(get("/api/v1/admin/countries"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.code == 'pl')].currency").value(hasItem("PLN")));
      mvc.perform(get("/api/v1/admin/car-types"))
          .andExpect(
              jsonPath("$[*].code")
                  .value(org.hamcrest.Matchers.containsInAnyOrder("SEDAN", "SUV", "VAN")));
      mvc.perform(get("/api/v1/admin/car-classes"))
          .andExpect(jsonPath("$[*].code").value(hasItem("PREMIUM")));
      mvc.perform(get("/api/v1/admin/locations/waw-airport").header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.timeZone").value("Europe/Warsaw"));
    }

    @Test
    void createsAndChangesACarType() throws Exception {
      putJson("/api/v1/admin/car-types/CABRIO", "{\"name\": \"Cabrio\", \"active\": true}")
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.code").value("CABRIO"));
      putJson("/api/v1/admin/car-types/CABRIO", "{\"name\": \"Cabrio\", \"active\": false}")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.active").value(false));
      putJson("/api/v1/admin/car-classes/LUXURY", "{\"name\": \"Luxury\", \"active\": true}")
          .andExpect(status().isCreated());
    }

    @Test
    void newCountryGetsItsOwnLocations() throws Exception {
      putJson(
              "/api/v1/admin/countries/fr",
              "{\"name\": \"France\", \"currency\": \"EUR\", \"active\": true}")
          .andExpect(status().isCreated());
      saveLocation("fr", "cdg-airport", "Europe/Paris").andExpect(status().isCreated());

      mvc.perform(get("/api/v1/admin/locations").header("X-Country", "fr"))
          .andExpect(jsonPath("$[*].id").value(org.hamcrest.Matchers.contains("cdg-airport")));
      mvc.perform(get("/api/v1/admin/locations/cdg-airport").header("X-Country", "pl"))
          .andExpect(status().isNotFound());
    }

    @Test
    void savingTheSameValueAgainChangesNothing() throws Exception {
      saveLocation("pl", "same-loc", "Europe/Warsaw").andExpect(status().isCreated());
      saveLocation("pl", "same-loc", "Europe/Warsaw").andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidReferenceData() throws Exception {
      putJson(
              "/api/v1/admin/countries/xx",
              "{\"name\": \"X\", \"currency\": \"ZZZ\", \"active\": true}")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-reference-data"));
      saveLocation("pl", "bad-zone", "Mars/Olympus")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-reference-data"));
      saveLocation("zz", "loc", "Europe/Warsaw")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
      mvc.perform(get("/api/v1/admin/locations")).andExpect(status().isBadRequest());
      putJson("/api/v1/admin/car-types/sedan", "{\"name\": \"Sedan\", \"active\": true}")
          .andExpect(status().isBadRequest());
      putJson("/api/v1/admin/car-types/SEDAN", "{\"active\": true}")
          .andExpect(status().isBadRequest());
    }

    @Test
    void returnsAllReferenceDataAtOnce() throws Exception {
      mvc.perform(get("/api/v1/reference-data"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.countries[*].code").value(hasItem("de")))
          .andExpect(jsonPath("$.carTypes[*].code").value(hasItem("VAN")))
          .andExpect(jsonPath("$.carClasses[*].code").value(hasItem("ECONOMY")))
          .andExpect(jsonPath("$.locations[*].id").value(hasItem("ber-airport")));
    }

    @Test
    void publishesItsOpenApiDescriptionAndHealth() throws Exception {
      mvc.perform(get("/openapi.yaml"))
          .andExpect(status().isOk())
          .andExpect(content().string(containsString("title: Administration Service")));
      mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("Events")
  class Events {

    @Test
    void everyChangeIsPublishedWithItsData() throws Exception {
      String id = "evt-" + UUID.randomUUID().toString().substring(0, 8);
      try (KafkaConsumer<String, String> consumer = consumer()) {
        saveLocation("pl", id, "Europe/Warsaw").andExpect(status().isCreated());
        saveLocation("pl", id, "Europe/Warsaw").andExpect(status().isOk());

        List<ConsumerRecord<String, String>> received =
            receive(consumer, "location:pl:" + id, Duration.ofSeconds(20));

        assertThat(received).hasSize(1);
        Map<?, ?> event = json.readValue(received.getFirst().value(), Map.class);
        assertThat(event.get("kind")).isEqualTo("LOCATION");
        assertThat(event.get("action")).isEqualTo("SAVED");
        assertThat(event.get("country")).isEqualTo("pl");
        assertThat(((Map<?, ?>) event.get("data")).get("timeZone")).isEqualTo("Europe/Warsaw");
        assertThat(((Map<?, ?>) event.get("data")).get("id")).isEqualTo(id);
      }
    }

    @Test
    void everyChangeIsAlsoAnAdministratorsActionForAnalytics() throws Exception {
      String id = "act-" + UUID.randomUUID().toString().substring(0, 8);
      saveLocation("pl", id, "Europe/Warsaw").andExpect(status().isCreated());

      String event =
          jdbc.sql(
                  "select payload::text from shared.outbox where topic = 'analytics'"
                      + " and payload->'data'->'data'->>'id' = :id")
              .param("id", id)
              .query(String.class)
              .single();
      assertThat((String) JsonPath.read(event, "$.type")).isEqualTo("admin.reference-data-changed");
      assertThat((String) JsonPath.read(event, "$.country")).isEqualTo("pl");
      assertThat((String) JsonPath.read(event, "$.service")).isEqualTo("administration-service");
      assertThat((String) JsonPath.read(event, "$.actor.kind")).isEqualTo("ADMINISTRATOR");
      assertThat((String) JsonPath.read(event, "$.data.kind")).isEqualTo("LOCATION");
      assertThat((Boolean) JsonPath.read(event, "$.data.created")).isTrue();
    }
  }

  private ResultActions saveLocation(String country, String id, String timeZone) throws Exception {
    return mvc.perform(
        put("/api/v1/admin/locations/{id}", id)
            .header("X-Country", country)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"name": "Test %s", "city": "City", "latitude": 52.2, "longitude": 21.0,
                 "timeZone": "%s", "active": true}
                """
                    .formatted(id, timeZone)));
  }

  private ResultActions putJson(String path, String body) throws Exception {
    return mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private KafkaConsumer<String, String> consumer() {
    Properties properties = new Properties();
    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID());
    properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    KafkaConsumer<String, String> consumer =
        new KafkaConsumer<>(properties, new StringDeserializer(), new StringDeserializer());
    consumer.subscribe(List.of("reference-data"));
    return consumer;
  }

  /** Waits until no new event of the key arrives for a moment, then returns all of them. */
  private static List<ConsumerRecord<String, String>> receive(
      KafkaConsumer<String, String> consumer, String key, Duration timeout) {
    List<ConsumerRecord<String, String>> received = new ArrayList<>();
    long deadline = System.nanoTime() + timeout.toNanos();
    long quietUntil = Long.MAX_VALUE;
    while (System.nanoTime() < Math.min(deadline, quietUntil)) {
      for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(250))) {
        if (key.equals(record.key())) {
          received.add(record);
          quietUntil = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        }
      }
    }
    return received;
  }
}
