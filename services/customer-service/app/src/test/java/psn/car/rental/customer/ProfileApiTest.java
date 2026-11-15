package psn.car.rental.customer;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.countryAdmin;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;
import static psn.car.rental.shared.security.TestTokens.service;
import static psn.car.rental.shared.security.TestTokens.superAdmin;

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

@SpringBootTest(properties = "logging.structured.format.console=")
@AutoConfigureMockMvc
@Import(TestInfrastructure.class)
@DisplayName("Driver profile API")
class ProfileApiTest {

  @Autowired MockMvc mvc;

  @Nested
  @DisplayName("A customer's own profile")
  class OwnProfile {

    @Test
    void isMissingUntilTheCustomerEntersIt() throws Exception {
      mvc.perform(get("/api/v1/profile").with(customer("new-customer")))
          .andExpect(status().isNotFound())
          .andExpect(content().contentType("application/problem+json"))
          .andExpect(jsonPath("$.type").value("/problems/profile-not-found"));
    }

    @Test
    void isSavedAndReadBack() throws Exception {
      save("customer-1", "1990-05-17", "PL")
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dateOfBirth").value("1990-05-17"))
          .andExpect(jsonPath("$.licenceCountry").value("pl"));

      mvc.perform(get("/api/v1/profile").with(customer("customer-1")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dateOfBirth").value("1990-05-17"))
          .andExpect(jsonPath("$.licenceCountry").value("pl"));
    }

    @Test
    void canBeChanged() throws Exception {
      save("customer-2", "1990-05-17", "pl").andExpect(status().isOk());
      save("customer-2", "1985-01-02", "de").andExpect(status().isOk());

      mvc.perform(get("/api/v1/profile").with(customer("customer-2")))
          .andExpect(jsonPath("$.dateOfBirth").value("1985-01-02"))
          .andExpect(jsonPath("$.licenceCountry").value("de"));
    }

    @Test
    void isNeverTheProfileOfAnotherCustomer() throws Exception {
      save("customer-3", "1990-05-17", "pl").andExpect(status().isOk());

      mvc.perform(get("/api/v1/profile").with(customer("customer-4")))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("A profile read by another service")
  class ForServices {

    @Test
    void isReadByTheCustomersId() throws Exception {
      save("customer-5", "2001-02-03", "de").andExpect(status().isOk());

      mvc.perform(get("/api/v1/profiles/{id}", "customer-5").with(service()))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.dateOfBirth").value("2001-02-03"))
          .andExpect(jsonPath("$.licenceCountry").value("de"));
      mvc.perform(get("/api/v1/profiles/{id}", "customer-unknown").with(service()))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("/problems/profile-not-found"));
    }

    @Test
    void isOnlyForServices() throws Exception {
      save("customer-6", "1990-05-17", "pl").andExpect(status().isOk());

      mvc.perform(get("/api/v1/profiles/{id}", "customer-6").with(customer("customer-7")))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/profiles/{id}", "customer-6").with(customer("customer-6")))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/profiles/{id}", "customer-6").with(superAdmin()))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/profiles/{id}", "customer-6").with(guest()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("Rules")
  class Rules {

    @Test
    void refusesADriverUnder18() throws Exception {
      save("customer-5", "2008-11-12", "pl")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-profile"))
          .andExpect(jsonPath("$.detail").value("A driver is at least 18 years old"));
      save("customer-5", "2008-11-11", "pl").andExpect(status().isOk());
    }

    @Test
    void refusesAnUnknownLicenceCountry() throws Exception {
      save("customer-6", "1990-05-17", "xx")
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-profile"));
    }

    @Test
    void refusesAMissingOrMalformedValue() throws Exception {
      mvc.perform(
              put("/api/v1/profile")
                  .with(customer("customer-7"))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"dateOfBirth\":\"1990-05-17\"}"))
          .andExpect(status().isBadRequest());
      save("customer-7", "17.05.1990", "pl").andExpect(status().isBadRequest());
    }
  }

  @Nested
  @DisplayName("Access")
  class Access {

    @Test
    void needsAToken() throws Exception {
      mvc.perform(get("/api/v1/profile").with(anonymous())).andExpect(status().isUnauthorized());
    }

    @Test
    void isOnlyForCustomers() throws Exception {
      mvc.perform(get("/api/v1/profile").with(guest())).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/profile").with(superAdmin())).andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/profile").with(countryAdmin("pl")))
          .andExpect(status().isForbidden());
      mvc.perform(get("/api/v1/profile").with(service())).andExpect(status().isForbidden());
    }

    @Test
    void keepsTheHealthCheckAndTheApiDescriptionOpen() throws Exception {
      mvc.perform(get("/actuator/health").with(anonymous())).andExpect(status().isOk());
      mvc.perform(get("/openapi.yaml").with(anonymous())).andExpect(status().isOk());
    }
  }

  private ResultActions save(String customerId, String dateOfBirth, String country)
      throws Exception {
    return mvc.perform(
        put("/api/v1/profile")
            .with(customer(customerId))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"dateOfBirth\":\"%s\",\"licenceCountry\":\"%s\"}"
                    .formatted(dateOfBirth, country)));
  }
}
