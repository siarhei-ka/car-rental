package psn.car.rental.info.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.guest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import psn.car.rental.shared.security.TestTokens;

@SpringBootTest(properties = "logging.structured.format.console=")
@AutoConfigureMockMvc
@Import({RedisTestInfrastructure.class, TestTokens.AllRolesByDefault.class})
@DisplayName("Access to the location info service")
class LocationInfoAccessTest {

  @Autowired MockMvc mvc;

  @Test
  void readsWithTheGuestTokenOfTheGateway() throws Exception {
    mvc.perform(get("/api/v1/countries").with(guest()))
        .andExpect(result ->
            assertThat(result.getResponse().getStatus())
                .as("passed the token check")
                .isNotIn(401, 403));
  }

  @Test
  void refusesARequestWithoutToken() throws Exception {
    mvc.perform(get("/api/v1/countries").with(anonymous())).andExpect(status().isUnauthorized());
  }

  @Test
  void refusesEveryChange() throws Exception {
    mvc.perform(put("/api/v1/countries").with(guest())).andExpect(status().isForbidden());
  }
}
