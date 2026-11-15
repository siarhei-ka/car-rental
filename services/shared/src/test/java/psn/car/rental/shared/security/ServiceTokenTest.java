package psn.car.rental.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

@DisplayName("Tokens of a calling service")
class ServiceTokenTest {

  private HttpServer server;
  private final AtomicInteger tokenRequests = new AtomicInteger();
  private final List<String> tokenAuthorizations = new CopyOnWriteArrayList<>();
  private final List<String> apiAuthorizations = new CopyOnWriteArrayList<>();
  private volatile int tokenStatus = 200;

  @BeforeEach
  void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/realms/car-rental/protocol/openid-connect/token",
        exchange -> {
          tokenRequests.incrementAndGet();
          tokenAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
          String body =
              tokenStatus == 200
                  ? "{\"access_token\":\"token-1\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                  : "{\"error\":\"unauthorized_client\"}";
          respond(exchange, tokenStatus, body);
        });
    server.createContext(
        "/api/v1/things",
        exchange -> {
          apiAuthorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
          respond(exchange, 200, "[]");
        });
    server.start();
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void sendsTheServiceTokenAndReusesItUntilItExpires() {
    RestClient client = client();

    client.get().uri("/api/v1/things").retrieve().toBodilessEntity();
    client.get().uri("/api/v1/things").retrieve().toBodilessEntity();

    assertThat(apiAuthorizations).containsExactly("Bearer token-1", "Bearer token-1");
    assertThat(tokenRequests).hasValue(1);
    String credentials =
        Base64.getEncoder()
            .encodeToString("search-service:secret-1".getBytes(StandardCharsets.UTF_8));
    assertThat(tokenAuthorizations).containsExactly("Basic " + credentials);
  }

  @Test
  void treatsAMissingTokenAsAnUnreachableDependency() {
    tokenStatus = 401;

    assertThatThrownBy(() -> client().get().uri("/api/v1/things").retrieve().toBodilessEntity())
        .isInstanceOf(ResourceAccessException.class)
        .hasMessageContaining("No service token");
    assertThat(apiAuthorizations).isEmpty();
  }

  @Test
  void isOnlyTurnedOnForAServiceWithAClient() {
    var runner =
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ServiceTokenAutoConfiguration.class));
    runner.run(context -> assertThat(context).doesNotHaveBean(RestClientCustomizer.class));
    runner
        .withPropertyValues("car-rental.security.client-id=search-service")
        .run(context -> assertThat(context).hasSingleBean(RestClientCustomizer.class));
  }

  private RestClient client() {
    URI base = URI.create("http://localhost:" + server.getAddress().getPort());
    var properties =
        new ServiceSecurityProperties(base, null, null, null, "search-service", "secret-1");
    return RestClient.builder()
        .baseUrl(base.toString())
        .requestInterceptor(ServiceTokenAutoConfiguration.interceptor(properties))
        .build();
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
