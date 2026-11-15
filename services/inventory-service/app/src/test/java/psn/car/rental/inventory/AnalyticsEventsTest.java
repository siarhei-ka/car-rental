package psn.car.rental.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import psn.car.rental.shared.security.TestTokens;

/** Changes of stock and transfers are recorded as administrators' actions, with the change. */
@IntegrationTest
@DisplayName("Analytics events of the inventory")
class AnalyticsEventsTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;

  List<String> events(String type) {
    return jdbc.sql(
            "select payload::text from public.outbox where payload->>'type' = :type"
                + " and payload->'data'->>'locationId' = 'ev-a' order by id")
        .param("type", type)
        .query(String.class)
        .list();
  }

  @Test
  void stockChangesAndTransfersAreRecorded() throws Exception {
    mvc.perform(
            put("/api/v1/admin/stock/ev-a/SEDAN/ECONOMY")
                .with(TestTokens.countryAdmin("pl"))
                .header("X-Country", "pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cars\": 3}"))
        .andExpect(status().isOk());
    mvc.perform(
            put("/api/v1/admin/transfers/tr-ev")
                .header("X-Country", "pl")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"locationId": "ev-a", "carType": "SEDAN", "carClass": "ECONOMY", "cars": 1,
                     "departure": "2026-12-05T08:00:00Z", "targetLocationId": "ev-b",
                     "plannedArrival": "2026-12-05T18:00:00Z"}
                    """))
        .andExpect(status().isCreated());
    mvc.perform(post("/api/v1/admin/transfers/tr-ev/arrival").header("X-Country", "pl"))
        .andExpect(status().isNoContent());

    String stock = events("admin.stock-changed").getLast();
    assertThat((String) JsonPath.read(stock, "$.country")).isEqualTo("pl");
    assertThat((String) JsonPath.read(stock, "$.actor.kind")).isEqualTo("ADMINISTRATOR");
    assertThat((Integer) JsonPath.read(stock, "$.data.cars")).isEqualTo(3);
    String dispatched = events("admin.transfer-dispatched").getLast();
    assertThat((String) JsonPath.read(dispatched, "$.data.targetLocationId")).isEqualTo("ev-b");
    assertThat((String) JsonPath.read(dispatched, "$.data.transferId")).isEqualTo("tr-ev");
    assertThat(events("admin.transfer-arrived")).hasSize(1);
  }
}
