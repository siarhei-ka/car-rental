package psn.car.rental.gateway;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.removeRequestHeader;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

import java.net.URI;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import psn.car.rental.shared.CorrelationId;

/**
 * Builds a route that passes matching requests on to a service. The service gets an access token
 * from {@link AccessTokens} and the correlation ID of the request (NFR-19), and never the browser's
 * cookies.
 */
final class Proxy {

  private Proxy() {}

  static RouterFunction<ServerResponse> to(
      AccessTokens tokens,
      String id,
      URI service,
      RequestPredicate requests,
      HandlerFilterFunction<ServerResponse, ServerResponse> filter) {
    return route(id)
        .route(requests, http())
        .before(uri(service))
        .before(removeRequestHeader("Cookie"))
        .before(Proxy::withCorrelationId)
        .filter(filter)
        .filter(tokens.relay())
        .build();
  }

  /** The ID the gateway accepted or created for this request, also when the browser sent none. */
  static ServerRequest withCorrelationId(ServerRequest request) {
    return CorrelationId.current()
        .map(
            id -> ServerRequest.from(request).headers(h -> h.set(CorrelationId.HEADER, id)).build())
        .orElse(request);
  }

  static RouterFunction<ServerResponse> to(
      AccessTokens tokens, String id, URI service, RequestPredicate requests) {
    return to(tokens, id, service, requests, (request, next) -> next.handle(request));
  }
}
