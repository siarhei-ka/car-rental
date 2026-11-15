package psn.car.rental.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses a request of a country administrator that names another country in the {@code X-Country}
 * header than the country in the administrator's token. The admin gateway does the same; this check
 * keeps the rule when a request reaches the service in another way.
 */
class CountryCheckFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Country";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String requested = request.getHeader(HEADER);
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (requested != null && authentication != null && limitedToOneCountry(authentication)) {
      Optional<String> own = ServiceRoles.country(authentication);
      if (own.isEmpty() || !own.get().equalsIgnoreCase(requested.strip())) {
        Refusals.forbidden(
            response,
            own.map(country -> "A country administrator works only with the country " + country)
                .orElse("The token names no country of the administrator"));
        return;
      }
    }
    chain.doFilter(request, response);
  }

  private static boolean limitedToOneCountry(Authentication authentication) {
    return ServiceRoles.has(authentication, ServiceRoles.COUNTRY_ADMIN)
        && !ServiceRoles.has(authentication, ServiceRoles.SUPER_ADMIN);
  }
}
