package psn.car.rental.ancillary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import psn.car.rental.shared.security.TestTokens;

/** An administrator's change is recorded for analytics in the transaction of the change. */
@IntegrationTest
@DisplayName("Analytics events")
class AnalyticsEventsTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;

  @Test
  void anAdministratorsChangeIsRecorded() throws Exception {
    mvc.perform(
            put("/api/v1/admin/extras/EV_SEAT")
                .with(TestTokens.countryAdmin("pl"))
                .header("X-Country", "pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Seat", "kind": "EQUIPMENT", "charge": "PER_DAY", "price": "12.50",
                     "maxQuantity": 2, "active": true}
                    """))
        .andExpect(status().is2xxSuccessful());

    String event =
        jdbc.sql(
                "select payload::text from public.outbox where payload->>'type' = 'admin.extra-changed'"
                    + " order by id desc limit 1")
            .query(String.class)
            .single();
    assertThat((String) JsonPath.read(event, "$.country")).isEqualTo("pl");
    assertThat((String) JsonPath.read(event, "$.actor.kind")).isEqualTo("ADMINISTRATOR");
    assertThat((String) JsonPath.read(event, "$.data.code")).isEqualTo("EV_SEAT");
    assertThat((String) JsonPath.read(event, "$.data.price.amount")).isEqualTo("12.50");
  }
}
