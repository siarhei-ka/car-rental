package psn.car.rental.gateway;

import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Sets the country of an administrator's request, which the services use as the tenant.
 *
 * <ul>
 *   <li>A country administrator works only in the country of their token. The gateway sets it in
 *       the {@code X-Country} header, and refuses a request that names another country.
 *   <li>A super administrator works in any country and names it in the {@code X-Country} header.
 * </ul>
 */
final class Tenant {

  static final String HEADER = "X-Country";

  private Tenant() {}

  static HandlerFilterFunction<ServerResponse, ServerResponse> filter() {
    return (request, next) -> {
      Authentication user = (Authentication) request.servletRequest().getUserPrincipal();
      if (user == null) {
        return refuse("Sign in to use this function.");
      }
      if (Roles.has(user, Roles.SUPER_ADMIN)) {
        return next.handle(request);
      }
      Optional<String> own = Roles.country(user);
      if (own.isEmpty()) {
        return refuse("This administrator account has no country.");
      }
      String requested = request.headers().firstHeader(HEADER);
      if (requested != null && !requested.equalsIgnoreCase(own.get())) {
        return refuse("This administrator may work only in the country " + own.get() + ".");
      }
      ServerRequest withCountry =
          ServerRequest.from(request).headers(headers -> headers.set(HEADER, own.get())).build();
      return next.handle(withCountry);
    };
  }

  private static ServerResponse refuse(String detail) {
    var problem = ProblemWriter.forbidden(detail);
    return ServerResponse.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }
}
