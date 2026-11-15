package psn.car.rental.pricing.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import psn.car.rental.pricing.config.PricingProperties;
import tools.jackson.databind.json.JsonMapper;

/**
 * The country list is loaded once at startup. When the administration service does not answer then,
 * for example while it is still starting, the list is asked again until it arrives.
 */
@DisplayName("Country list at startup")
class CountrySyncRetryTest {

  HttpServer administration;
  final AtomicInteger requests = new AtomicInteger();
  final TenantMigrations migrations = mock(TenantMigrations.class);
  CountrySync sync;

  @BeforeEach
  void start() throws Exception {
    administration = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    administration.createContext(
        "/api/v1/reference-data",
        exchange -> {
          boolean first = requests.incrementAndGet() == 1;
          byte[] body =
              (first
                      ? "{}"
                      : "{\"countries\":[{\"code\":\"pl\",\"currency\":\"PLN\",\"active\":true}]}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(first ? 503 : 200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    administration.start();
    URI url = URI.create("http://localhost:" + administration.getAddress().getPort());
    sync =
        new CountrySync(
            migrations,
            JsonMapper.builder().build(),
            RestClient.builder(),
            new PricingProperties(
                Map.of(), false, url, null, null, null, null, null, null, 30, 21));
  }

  @AfterEach
  void stop() {
    administration.stop(0);
  }

  @Test
  void asksAgainUntilTheListArrivesAndThenStops() {
    sync.loadAtStartup();
    verify(migrations, never()).ensure(new Country("pl"), true, "PLN");

    sync.retryUntilLoaded();
    verify(migrations).ensure(new Country("pl"), true, "PLN");

    sync.retryUntilLoaded();
    assertThat(requests).hasValue(2);
  }
}
