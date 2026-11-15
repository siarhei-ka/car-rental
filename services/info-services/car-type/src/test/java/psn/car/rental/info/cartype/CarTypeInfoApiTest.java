package psn.car.rental.info.cartype;

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
class CarTypeInfoApiTest {

  @Autowired MockMvc mvc;
  @Autowired StringRedisTemplate redis;

  @BeforeEach
  void fillRedis() {
    redis.getConnectionFactory().getConnection().serverCommands().flushAll();
    redis
        .opsForHash()
        .put("ref:car-types", "VAN", "{\"code\":\"VAN\",\"name\":\"Van\",\"active\":true}");
    redis
        .opsForHash()
        .put("ref:car-types", "SEDAN", "{\"code\":\"SEDAN\",\"name\":\"Sedan\",\"active\":true}");
    redis
        .opsForHash()
        .put(
            "ref:car-types",
            "CABRIO",
            "{\"code\":\"CABRIO\",\"name\":\"Cabrio\",\"active\":false}");
    redis
        .opsForHash()
        .put(
            "ref:car-classes",
            "ECONOMY",
            "{\"code\":\"ECONOMY\",\"name\":\"Economy\",\"active\":true}");
    redis.opsForValue().set("ref:loaded", "2026-11-10T22:00:00Z");
  }

  @Test
  void listsActiveCarTypesAndClasses() throws Exception {
    mvc.perform(get("/api/v1/car-types"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].code").value(contains("SEDAN", "VAN")));
    mvc.perform(get("/api/v1/car-classes"))
        .andExpect(jsonPath("$[*].code").value(contains("ECONOMY")));
  }

  @Test
  void returnsOneCarTypeEvenWhenItIsNotActive() throws Exception {
    mvc.perform(get("/api/v1/car-types/CABRIO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    mvc.perform(get("/api/v1/car-types/TRUCK")).andExpect(status().isNotFound());
  }

  @Test
  void answers503UntilReferenceDataIsLoaded() throws Exception {
    redis.delete("ref:loaded");

    mvc.perform(get("/api/v1/car-types"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("/problems/reference-data-unavailable"));
  }

  @Test
  void publishesItsOpenApiDescriptionAndHealth() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("title: Car Type Info Service")));
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }
}
