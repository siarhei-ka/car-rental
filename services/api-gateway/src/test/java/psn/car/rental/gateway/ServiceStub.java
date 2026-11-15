package psn.car.rental.gateway;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A service behind the gateway. It answers every request with 200 and records what it got. As
 * Keycloak it answers a token request with the token {@link #TOKEN}.
 */
final class ServiceStub implements AutoCloseable {

  static final String TOKEN = "gateway-token-1";

  record Received(String method, String path, String query, Map<String, List<String>> headers) {

    String header(String name) {
      return headers.entrySet().stream()
          .filter(e -> e.getKey().equalsIgnoreCase(name))
          .map(e -> e.getValue().getFirst())
          .findFirst()
          .orElse(null);
    }
  }

  private final HttpServer server;
  private final List<Received> received = new CopyOnWriteArrayList<>();

  ServiceStub() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(
        "/",
        exchange -> {
          received.add(
              new Received(
                  exchange.getRequestMethod(),
                  exchange.getRequestURI().getPath(),
                  exchange.getRequestURI().getRawQuery(),
                  Map.copyOf(exchange.getRequestHeaders())));
          exchange.getRequestBody().readAllBytes();
          String text =
              exchange.getRequestURI().getPath().endsWith("/protocol/openid-connect/token")
                  ? "{\"access_token\":\"%s\",\"token_type\":\"Bearer\",\"expires_in\":300}"
                      .formatted(TOKEN)
                  : "{\"ok\":true}";
          byte[] body = text.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
  }

  URI uri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  Received last() {
    return received.getLast();
  }

  int count() {
    return received.size();
  }

  void reset() {
    received.clear();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
