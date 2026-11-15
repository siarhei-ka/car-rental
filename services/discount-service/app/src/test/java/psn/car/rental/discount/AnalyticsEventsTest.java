package psn.car.rental.discount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
            post("/api/v1/admin/discount-rules")
                .with(TestTokens.countryAdmin("pl"))
                .header("X-Country", "pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "Event rule", "conditions": {"minDays": 4},
                     "effect": {"kind": "PERCENT_OFF", "percent": "7", "target": "TOTAL"},
                     "validFrom": "2026-01-01", "validTo": null, "active": true}
                    """))
        .andExpect(status().is2xxSuccessful());

    String event =
        jdbc.sql(
                "select payload::text from public.outbox where payload->>'type' = 'admin.discount-rule-created'"
                    + " order by id desc limit 1")
            .query(String.class)
            .single();
    assertThat((String) JsonPath.read(event, "$.country")).isEqualTo("pl");
    assertThat((String) JsonPath.read(event, "$.actor.kind")).isEqualTo("ADMINISTRATOR");
    assertThat((String) JsonPath.read(event, "$.data.name")).isEqualTo("Event rule");
    assertThat((String) JsonPath.read(event, "$.data.percentOff")).isEqualTo("7");
  }
}
