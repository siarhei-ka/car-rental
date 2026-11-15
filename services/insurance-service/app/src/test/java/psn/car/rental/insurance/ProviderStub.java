package psn.car.rental.insurance;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Stands in for the insurance provider in tests. It records each request and answers with the
 * configured price, an error or a delay.
 */
final class ProviderStub {

  private final HttpServer server;
  final List<String> bodies = new CopyOnWriteArrayList<>();
  final List<String> apiKeys = new CopyOnWriteArrayList<>();
  final List<String> authorizations = new CopyOnWriteArrayList<>();
  volatile int status = 200;
  volatile long delayMillis = 0;
  volatile String currency = null;

  ProviderStub() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext(
        "/v1/quotes",
        exchange -> {
          String body =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          bodies.add(body);
          apiKeys.add(exchange.getRequestHeaders().getFirst("X-Api-Key"));
          authorizations.add(
              String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
          try {
            Thread.sleep(delayMillis);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          String product = body.contains("\"FULL\"") ? "FULL" : "BASIC";
          String requested = body.replaceAll(".*\"currency\":\"([A-Z]{3})\".*", "$1");
          String price =
              (product.equals("FULL") ? "50" : "10")
                  + (body.contains("driverAge\":2") ? ".50" : ".00");
          byte[] answer =
              (status == 200
                      ? "{\"product\":\"%s\",\"pricePerDay\":\"%s\",\"currency\":\"%s\",\"factors\":[]}"
                          .formatted(product, price, currency == null ? requested : currency)
                      : "{\"detail\":\"broken\"}")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, answer.length);
          exchange.getResponseBody().write(answer);
          exchange.close();
        });
    server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
    server.start();
  }

  URI uri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  void reset() {
    bodies.clear();
    apiKeys.clear();
    authorizations.clear();
    status = 200;
    delayMillis = 0;
    currency = null;
  }
}
