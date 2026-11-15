package psn.car.rental.insurance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static psn.car.rental.shared.security.TestTokens.customer;
import static psn.car.rental.shared.security.TestTokens.guest;
import static psn.car.rental.shared.security.TestTokens.service;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import psn.car.rental.insurance.tenant.Countries;
import psn.car.rental.insurance.tenant.Country;
import psn.car.rental.shared.security.TestTokens;

@SpringBootTest(
    properties = {
      "insurance.countries.pl=PLN",
      "insurance.countries.de=EUR",
      "insurance.administration-url=http://administration.invalid",
      "insurance.provider-api-key=key-1",
      "insurance.provider-timeout=PT0.5S",
      "logging.structured.format.console="
    })
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, TestTokens.AllRolesByDefault.class})
@DisplayName("Insurance API")
class InsuranceApiTest {

  static final ProviderStub provider = new ProviderStub();

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("insurance.provider-url", () -> provider.uri().toString());
  }

  @AfterAll
  static void stop() {
    provider.reset();
  }

  @Autowired MockMvc mvc;

  @BeforeEach
  void reset() {
    provider.reset();
  }

  @Nested
  @DisplayName("Options")
  class Options {

    @Test
    void listsBothOptionsWithTheBasePriceOfTheProviderInTheCurrencyOfTheCountry() throws Exception {
      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "pl"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].code").value("BASIC"))
          .andExpect(jsonPath("$[0].preselected").value(true))
          .andExpect(jsonPath("$[0].pricePerDay.amount").value("10.00"))
          .andExpect(jsonPath("$[0].pricePerDay.currency").value("PLN"))
          .andExpect(jsonPath("$[1].code").value("FULL"))
          .andExpect(jsonPath("$[1].preselected").value(false))
          .andExpect(jsonPath("$[1].pricePerDay.amount").value("50.00"));

      assertThat(provider.bodies).hasSize(2);
      assertThat(provider.bodies.getFirst())
          .contains("\"rentalCountry\":\"pl\"", "\"currency\":\"PLN\"")
          .doesNotContain("driverAge\":2", "licenceCountry\":\"");
      assertThat(provider.apiKeys).containsOnly("key-1");
      assertThat(provider.authorizations).as("no token of the system").containsOnly("null");
    }

    @Test
    void asksInTheCurrencyOfEachCountry() throws Exception {
      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "de"))
          .andExpect(jsonPath("$[0].pricePerDay.currency").value("EUR"));
    }
  }

  @Nested
  @DisplayName("Price for a driver")
  class Quotes {

    @Test
    void sendsOnlyTheAgeAndTheLicenceCountryOfTheDriver() throws Exception {
      mvc.perform(quote("FULL", 24, "ua"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value("FULL"))
          .andExpect(jsonPath("$.pricePerDay.amount").value("50.50"));

      assertThat(provider.bodies)
          .singleElement()
          .asString()
          .contains("\"driverAge\":24", "\"licenceCountry\":\"ua\"", "\"rentalCountry\":\"pl\"");
    }

    @Test
    void refusesAnUnknownOptionOrDriver() throws Exception {
      mvc.perform(quote("GOLD", 30, "pl"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/unknown-insurance-option"));
      mvc.perform(quote("BASIC", 16, "pl"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/invalid-insurance-request"));
      mvc.perform(
              post("/api/v1/insurance-quotes")
                  .header("X-Country", "pl")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"option\":\"BASIC\"}"))
          .andExpect(status().isBadRequest());
      assertThat(provider.bodies).isEmpty();
    }
  }

  @Nested
  @DisplayName("When the provider fails")
  class ProviderFailure {

    @Test
    void answers503AndInventsNoPriceWhenTheProviderFails() throws Exception {
      provider.status = 500;

      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "pl"))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.type").value("/problems/insurance-unavailable"));
    }

    @Test
    void givesUpAfterTheTimeLimit() throws Exception {
      provider.delayMillis = 1_500;
      Instant started = Instant.now();

      mvc.perform(quote("BASIC", 30, "pl")).andExpect(status().isServiceUnavailable());

      assertThat(Duration.between(started, Instant.now())).isLessThan(Duration.ofMillis(1_400));
    }

    @Test
    void refusesAnAnswerInAnotherCurrency() throws Exception {
      provider.currency = "USD";

      mvc.perform(quote("BASIC", 30, "pl")).andExpect(status().isServiceUnavailable());
    }
  }

  @Nested
  @DisplayName("Access and countries")
  class Access {

    @Test
    void showsOptionsToEveryValidTokenAndPricesForADriverOnlyToServices() throws Exception {
      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "pl").with(guest()))
          .andExpect(status().isOk());
      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "pl").with(anonymous()))
          .andExpect(status().isUnauthorized());
      mvc.perform(quote("BASIC", 30, "pl").with(service())).andExpect(status().isOk());
      mvc.perform(quote("BASIC", 30, "pl").with(customer())).andExpect(status().isForbidden());
    }

    @Test
    void refusesAnUnknownCountry() throws Exception {
      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "it"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("/problems/unknown-country"));
    }
  }

  @Nested
  @DisplayName("Country list")
  class CountryList {

    @Autowired KafkaTemplate<String, String> kafka;
    @Autowired Countries countries;

    @Test
    void servesANewCountryInItsCurrency() throws Exception {
      kafka.send(
          "reference-data",
          "country:cz",
          """
          {"kind":"COUNTRY","data":{"code":"cz","name":"Czechia","currency":"CZK","active":true}}
          """);
      Instant deadline = Instant.now().plusSeconds(30);
      while (!countries.isServed(new Country("cz"))) {
        assertThat(Instant.now()).isBefore(deadline);
        Thread.sleep(100);
      }

      mvc.perform(get("/api/v1/insurance-options").header("X-Country", "cz"))
          .andExpect(jsonPath("$[0].pricePerDay.currency").value("CZK"));
    }
  }

  private static MockHttpServletRequestBuilder quote(String option, int age, String licence) {
    return post("/api/v1/insurance-quotes")
        .header("X-Country", "pl")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"option\":\"%s\",\"driverAge\":%d,\"licenceCountry\":\"%s\"}"
                .formatted(option, age, licence));
  }
}
