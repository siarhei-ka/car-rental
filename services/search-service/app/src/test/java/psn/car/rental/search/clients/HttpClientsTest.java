package psn.car.rental.search.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import psn.car.rental.search.SearchProperties;
import psn.car.rental.search.application.DependencyUnavailableException;
import psn.car.rental.search.domain.Availability;
import psn.car.rental.search.domain.CarKind;
import psn.car.rental.search.domain.DailyRate;
import psn.car.rental.search.domain.Location;
import psn.car.rental.search.domain.Price;
import psn.car.rental.search.domain.SearchQuery;

/** The HTTP clients against a real HTTP server, including the time limit. */
class HttpClientsTest {

  private HttpServer server;
  private SearchProperties properties;
  private final AtomicReference<String> lastRequest = new AtomicReference<>();

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    respond(
        "/api/v1/locations/pl/waw",
        200,
        "{\"id\":\"waw\",\"country\":\"pl\",\"name\":\"Warsaw\",\"city\":\"Warsaw\",\"timeZone\":\"Europe/Warsaw\",\"active\":true}");
    respond("/api/v1/locations/pl/none", 404, "{\"type\":\"/problems/not-found\"}");
    respond("/api/v1/locations/pl/broken", 500, "{}");
    respond("/api/v1/car-types", 200, "[{\"code\":\"SEDAN\",\"name\":\"Sedan\",\"active\":true}]");
    respond(
        "/api/v1/car-classes",
        200,
        "[{\"code\":\"ECONOMY\",\"name\":\"Economy\",\"active\":true}]");
    respond(
        "/api/v1/availability",
        200,
        "[{\"carType\":\"SEDAN\",\"carClass\":\"ECONOMY\",\"availableCars\":3}]");
    respond(
        "/api/v1/rates",
        200,
        "[{\"locationId\":\"waw\",\"carType\":\"SEDAN\",\"carClass\":\"ECONOMY\","
            + "\"dailyRate\":{\"amount\":\"149.00\",\"currency\":\"PLN\"}}]");
    server.createContext(
        "/api/v1/locations/pl/slow",
        exchange -> {
          try {
            Thread.sleep(1_500);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          exchange.sendResponseHeaders(200, -1);
          exchange.close();
        });
    server.start();
    URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    properties = new SearchProperties(base, base, base, base, 30, Duration.ofMillis(500));
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void readsLocationsAndTreatsMissingOnesAsEmpty() {
    var locations = new HttpClients().locations(RestClient.builder(), properties);

    assertThat(locations.find("pl", "waw"))
        .contains(new Location("waw", "pl", "Warsaw", "Europe/Warsaw", true));
    assertThat(locations.find("pl", "none")).isEqualTo(Optional.empty());
    assertThatThrownBy(() -> locations.find("pl", "broken"))
        .isInstanceOf(DependencyUnavailableException.class);
  }

  @Test
  void givesUpAfterTheTimeLimit() {
    var locations = new HttpClients().locations(RestClient.builder(), properties);

    long started = System.nanoTime();
    assertThatThrownBy(() -> locations.find("pl", "slow"))
        .isInstanceOf(DependencyUnavailableException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1_400));
  }

  @Test
  void readsCarTypesClassesAndAvailability() {
    var catalog = new HttpClients().carCatalog(RestClient.builder(), properties);
    var inventory = new HttpClients().inventory(RestClient.builder(), properties);

    assertThat(catalog.carTypes()).containsExactly(new CarKind("SEDAN", "Sedan", true));
    assertThat(catalog.carClasses()).containsExactly(new CarKind("ECONOMY", "Economy", true));
    List<Availability> availability =
        inventory.availability(
            new SearchQuery(
                "pl",
                "waw",
                "krk",
                Instant.parse("2026-12-10T10:00:00Z"),
                3,
                Optional.empty(),
                Optional.empty()));
    assertThat(availability).containsExactly(new Availability("SEDAN", "ECONOMY", 3));
    assertThat(lastRequest.get())
        .contains("locationId=waw")
        .contains("returnLocationId=krk")
        .contains("days=3")
        .contains("X-Country=pl");
  }

  @Test
  void readsTheDailyRatesOfTheLocation() {
    var prices = new HttpClients().priceList(RestClient.builder(), properties);

    assertThat(prices.rates("pl", "waw"))
        .containsExactly(new DailyRate("SEDAN", "ECONOMY", new Price("149.00", "PLN")));
    assertThat(lastRequest.get()).contains("locationId=waw").contains("X-Country=pl");
  }

  private void respond(String path, int status, String body) {
    server.createContext(
        path,
        exchange -> {
          lastRequest.set(
              exchange.getRequestURI().getQuery()
                  + " X-Country="
                  + exchange.getRequestHeaders().getFirst("X-Country"));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
  }
}
