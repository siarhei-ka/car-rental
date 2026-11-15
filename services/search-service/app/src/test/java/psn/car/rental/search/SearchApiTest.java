package psn.car.rental.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import psn.car.rental.search.application.CarCatalog;
import psn.car.rental.search.application.DependencyUnavailableException;
import psn.car.rental.search.application.Inventory;
import psn.car.rental.search.application.Locations;
import psn.car.rental.search.application.PriceList;
import psn.car.rental.search.domain.Availability;
import psn.car.rental.search.domain.CarKind;
import psn.car.rental.search.domain.DailyRate;
import psn.car.rental.search.domain.Location;
import psn.car.rental.search.domain.Price;
import psn.car.rental.shared.security.TestTokens;

@SpringBootTest(properties = "logging.structured.format.console=")
@AutoConfigureMockMvc
@Import({SearchApiTest.Fakes.class, TestTokens.AllRolesByDefault.class})
class SearchApiTest {

  @Autowired MockMvc mvc;
  @Autowired RecordedEvents events;

  @Test
  void recordsEverySearchWithoutTheCustomer() throws Exception {
    events.clear();
    mvc.perform(search().with(TestTokens.customer("someone")).param("carType", "SUV"))
        .andExpect(status().isOk());

    assertThat(events).hasSize(1);
    Object[] event = events.getFirst();
    assertThat(event[0]).isEqualTo("search.performed");
    assertThat(event[1]).isEqualTo("pl");
    assertThat(event[2]).isEqualTo(psn.car.rental.shared.events.Actor.VISITOR);
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) event[3];
    assertThat(data)
        .containsEntry("pickUpLocationId", "waw-airport")
        .containsEntry("carType", "SUV")
        .containsEntry("offers", 1)
        .doesNotContainKey("customerId");
  }

  @Test
  void acceptsTheGuestTokenOfTheGatewayButNoRequestWithoutToken() throws Exception {
    mvc.perform(search().with(TestTokens.guest())).andExpect(status().isOk());
    mvc.perform(search().with(anonymous())).andExpect(status().isUnauthorized());
  }

  @Test
  void findsTheFreeCarsOfALocation() throws Exception {
    mvc.perform(search().param("returnLocationId", "krk-airport"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pickUpLocation.name").value("Warsaw Airport"))
        .andExpect(jsonPath("$.returnLocation.id").value("krk-airport"))
        .andExpect(jsonPath("$.end").value("2026-12-13T10:00:00Z"))
        .andExpect(jsonPath("$.offers", hasSize(2)))
        .andExpect(jsonPath("$.offers[0].carTypeName").value("Sedan"))
        .andExpect(jsonPath("$.offers[1].carType").value("SUV"))
        .andExpect(jsonPath("$.offers[1].availableCars").value(2))
        .andExpect(jsonPath("$.offers[1].dailyRate.amount").value("219.00"))
        .andExpect(jsonPath("$.offers[1].dailyRate.currency").value("PLN"));
  }

  @Test
  void filtersByCarType() throws Exception {
    mvc.perform(search().param("carType", "SUV"))
        .andExpect(jsonPath("$.offers", hasSize(1)))
        .andExpect(jsonPath("$.returnLocation.id").value("waw-airport"));
  }

  @Test
  void rejectsInvalidSearches() throws Exception {
    mvc.perform(search().param("returnLocationId", "ber-airport"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.type").value("/problems/invalid-search"));
    mvc.perform(search().param("returnLocationId", "closed"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("not open")));
    mvc.perform(
            get("/api/v1/search")
                .header("X-Country", "pl")
                .param("pickUpLocationId", "waw-airport")
                .param("start", "2026-11-30T10:00:00Z")
                .param("days", "1"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("The start must be in the future"));
    mvc.perform(
            get("/api/v1/search")
                .header("X-Country", "XX")
                .param("pickUpLocationId", "waw-airport")
                .param("start", "2026-12-10T10:00:00Z")
                .param("days", "3"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail", containsString("country")));
    mvc.perform(get("/api/v1/search").header("X-Country", "pl")).andExpect(status().isBadRequest());
  }

  @Test
  void answers503WhenADependencyIsDown() throws Exception {
    mvc.perform(
            get("/api/v1/search")
                .header("X-Country", "pl")
                .param("pickUpLocationId", "down")
                .param("start", "2026-12-10T10:00:00Z")
                .param("days", "3"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.type").value("/problems/search-unavailable"));
  }

  @Test
  void publishesItsOpenApiDescriptionAndHealth() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("title: Search Service")));
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder search() {
    return get("/api/v1/search")
        .header("X-Country", "pl")
        .param("pickUpLocationId", "waw-airport")
        .param("start", "2026-12-10T10:00:00Z")
        .param("days", "3");
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Fakes {

    static final Map<String, Location> LOCATIONS =
        Map.of(
            "pl:waw-airport",
                new Location("waw-airport", "pl", "Warsaw Airport", "Europe/Warsaw", true),
            "pl:krk-airport",
                new Location("krk-airport", "pl", "Krakow Airport", "Europe/Warsaw", true),
            "pl:closed", new Location("closed", "pl", "Closed", "Europe/Warsaw", false),
            "de:waw-airport", new Location("waw-airport", "de", "Wrong", "Europe/Berlin", true));

    @Bean
    RecordedEvents recordedEvents() {
      return new RecordedEvents();
    }

    /** Records the analytics events of the search instead of sending them to Kafka. */
    @Bean
    @Primary
    psn.car.rental.shared.events.Analytics recordingAnalytics(RecordedEvents events) {
      return (type, country, actor, data) -> events.add(new Object[] {type, country, actor, data});
    }

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-12-01T08:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    @Primary
    Locations fakeLocations() {
      return (country, id) -> {
        if (id.equals("down")) {
          throw new DependencyUnavailableException(
              "The location info service is not available", null);
        }
        return Optional.ofNullable(LOCATIONS.get(country + ":" + id));
      };
    }

    @Bean
    @Primary
    CarCatalog fakeCatalog() {
      return new CarCatalog() {
        @Override
        public List<CarKind> carTypes() {
          return List.of(new CarKind("SEDAN", "Sedan", true), new CarKind("SUV", "SUV", true));
        }

        @Override
        public List<CarKind> carClasses() {
          return List.of(new CarKind("ECONOMY", "Economy", true));
        }
      };
    }

    @Bean
    @Primary
    PriceList fakePrices() {
      return (country, locationId) ->
          List.of(
              new DailyRate("SUV", "ECONOMY", new Price("219.00", "PLN")),
              new DailyRate("SEDAN", "ECONOMY", new Price("149.00", "PLN")),
              new DailyRate("VAN", "ECONOMY", new Price("259.00", "PLN")));
    }

    @Bean
    @Primary
    Inventory fakeInventory() {
      return query ->
          List.of(
              new Availability("SUV", "ECONOMY", 2),
              new Availability("SEDAN", "ECONOMY", 1),
              new Availability("VAN", "ECONOMY", 0));
    }
  }

  /** The analytics events recorded by the search. */
  static class RecordedEvents extends java.util.concurrent.CopyOnWriteArrayList<Object[]> {}
}
