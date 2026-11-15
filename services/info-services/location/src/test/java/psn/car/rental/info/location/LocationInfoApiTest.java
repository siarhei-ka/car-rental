package psn.car.rental.info.location;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import psn.car.rental.shared.security.TestTokens;

@SpringBootTest(properties = "logging.structured.format.console=")
@AutoConfigureMockMvc
@Import({RedisTestInfrastructure.class, TestTokens.AllRolesByDefault.class})
class LocationInfoApiTest {

  @Autowired MockMvc mvc;
  @Autowired StringRedisTemplate redis;

  @BeforeEach
  void fillRedis() {
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    redis
        .opsForHash()
        .put(
            "ref:countries",
            "pl",
            "{\"code\":\"pl\",\"name\":\"Poland\",\"currency\":\"PLN\",\"active\":true}");
    redis
        .opsForHash()
        .put(
            "ref:countries",
            "de",
            "{\"code\":\"de\",\"name\":\"Germany\",\"currency\":\"EUR\",\"active\":true}");
    redis
        .opsForHash()
        .put(
            "ref:countries",
            "it",
            "{\"code\":\"it\",\"name\":\"Italy\",\"currency\":\"EUR\",\"active\":false}");
    redis.opsForHash().put("ref:locations:pl", "waw-airport", location("pl", "waw-airport", true));
    redis.opsForHash().put("ref:locations:pl", "waw-center", location("pl", "waw-center", false));
    redis.opsForHash().put("ref:locations:de", "ber-airport", location("de", "ber-airport", true));
    redis.opsForHash().put("ref:locations:it", "fco-airport", location("it", "fco-airport", true));
    redis.opsForValue().set("ref:loaded", "2026-11-10T22:00:00Z");
  }

  @Test
  void listsActiveCountries() throws Exception {
    mvc.perform(get("/api/v1/countries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code").value(contains("de", "pl")))
        .andExpect(jsonPath("$[1].currency").value("PLN"));
  }

  @Test
  void listsActiveLocationsOfActiveCountries() throws Exception {
    mvc.perform(get("/api/v1/locations"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].id").value(contains("ber-airport", "waw-airport")));
    mvc.perform(get("/api/v1/locations").param("country", "pl"))
        .andExpect(jsonPath("$[*].id").value(contains("waw-airport")))
        .andExpect(jsonPath("$[0].timeZone").value("Europe/Warsaw"));
    mvc.perform(get("/api/v1/locations").param("country", "PL")).andExpect(status().isBadRequest());
  }

  @Test
  void returnsOneLocationEvenWhenItIsNotActive() throws Exception {
    mvc.perform(get("/api/v1/locations/pl/waw-center"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    mvc.perform(get("/api/v1/locations/pl/missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.type").value("/problems/not-found"));
  }

  @Test
  void answers503UntilReferenceDataIsLoaded() throws Exception {
    redis.delete("ref:loaded");

    mvc.perform(get("/api/v1/countries"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("/problems/reference-data-unavailable"));
  }

  @Test
  void publishesItsOpenApiDescriptionAndHealth() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("title: Location Info Service")));
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  private static String location(String country, String id, boolean active) {
    return """
    {"id":"%s","country":"%s","name":"Name %s","city":"City","latitude":52.2,"longitude":21.0,\
    "timeZone":"Europe/Warsaw","active":%s}\
    """
        .formatted(id, country, id, active);
  }
}
