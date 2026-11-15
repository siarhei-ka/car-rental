package psn.car.rental.pricing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Stands in for every service a quote depends on: location info, customer, insurance, ancillary and
 * discount. It answers with fixed example data, records each request, and can be told to fail or to
 * answer late on a path.
 */
final class DependencyStub {

  static final DependencyStub INSTANCE = new DependencyStub();

  /** A request the stub received. */
  record Request(String method, String path, String country, String body) {}

  final List<Request> requests = new CopyOnWriteArrayList<>();
  final Map<String, Integer> failures = new ConcurrentHashMap<>();
  final Map<String, Long> delays = new ConcurrentHashMap<>();
  volatile String discount = "null";

  private static final Map<String, String> LOCATIONS =
      Map.of(
          "/api/v1/locations/pl/waw-airport", location("waw-airport", "pl", "Europe/Warsaw", true),
          "/api/v1/locations/pl/krk", location("krk", "pl", "Europe/Warsaw", true),
          "/api/v1/locations/pl/closed", location("closed", "pl", "Europe/Warsaw", false),
          "/api/v1/locations/de/ber", location("ber", "de", "Europe/Berlin", true),
          "/api/v1/locations/de/muc", location("muc", "de", "Europe/Berlin", true));

  private static final Map<String, String> PROFILES =
      Map.of(
          "/api/v1/profiles/driver-30", profile("1996-01-01", "pl"),
          "/api/v1/profiles/driver-20", profile("2005-12-11", "pl"),
          "/api/v1/profiles/driver-de", profile("1980-03-04", "de"));

  private final HttpServer server;

  private DependencyStub() {
    try {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    server.createContext("/", this::handle);
    server.setExecutor(Executors.newCachedThreadPool());
    server.start();
  }

  URI uri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  void reset() {
    requests.clear();
    failures.clear();
    delays.clear();
    discount = "null";
  }

  List<Request> to(String path) {
    return requests.stream().filter(r -> r.path().equals(path)).toList();
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String country = exchange.getRequestHeaders().getFirst("X-Country");
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(new Request(exchange.getRequestMethod(), path, country, body));
    long delay = delays.getOrDefault(path, 0L);
    if (delay > 0) {
      try {
        Thread.sleep(delay);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    Integer failure = failures.get(path);
    if (failure != null) {
      send(exchange, failure, "{\"detail\":\"broken\"}");
      return;
    }
    String currency = "de".equals(country) ? "EUR" : "PLN";
    String answer =
        switch (path) {
          case "/api/v1/insurance-quotes" -> insurance(body, currency);
          case "/api/v1/extras" ->
              """
              [{"code":"CHILD_SEAT","name":"Child seat","kind":"EQUIPMENT","charge":"PER_DAY",
                "price":{"amount":"25.00","currency":"%1$s"},"maxQuantity":3,"active":true},
               {"code":"ROAD_TOLL","name":"Road toll payment","kind":"SERVICE","charge":"PER_RENTAL",
                "price":{"amount":"49.00","currency":"%1$s"},"maxQuantity":1,"active":true}]
              """
                  .formatted(currency);
          case "/api/v1/one-way-fee" ->
              "pl".equals(country) ? "{\"amount\":\"150.00\",\"currency\":\"PLN\"}" : null;
          case "/api/v1/discount-evaluations" -> "{\"discount\":" + discount + "}";
          default -> LOCATIONS.getOrDefault(path, PROFILES.get(path));
        };
    if (answer == null) {
      send(exchange, 404, "{\"detail\":\"not found\"}");
    } else if (answer.startsWith("400:")) {
      send(exchange, 400, answer.substring(4));
    } else {
      send(exchange, 200, answer);
    }
  }

  private static String insurance(String body, String currency) {
    if (body.contains("\"GOLD\"")) {
      return "400:{\"detail\":\"There is no insurance option GOLD\"}";
    }
    boolean full = body.contains("\"FULL\"");
    return """
    {"code":"%s","name":"%s","description":"","preselected":%s,
     "pricePerDay":{"amount":"%s","currency":"%s"}}
    """
        .formatted(
            full ? "FULL" : "BASIC",
            full ? "Full cover" : "Basic cover",
            !full,
            full ? "50.00" : "10.00",
            currency);
  }

  private static void send(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  private static String location(String id, String country, String zone, boolean active) {
    return """
    {"id":"%s","country":"%s","name":"%s","city":"City","latitude":0,"longitude":0,
     "timeZone":"%s","active":%s}
    """
        .formatted(id, country, id, zone, active);
  }

  private static String profile(String dateOfBirth, String country) {
    return "{\"dateOfBirth\":\"%s\",\"licenceCountry\":\"%s\"}".formatted(dateOfBirth, country);
  }
}
