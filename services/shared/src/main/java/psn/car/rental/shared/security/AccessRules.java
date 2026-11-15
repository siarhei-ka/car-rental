package psn.car.rental.shared.security;

import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * The access rules of one service. A service declares them as a bean, and the shared security
 * configuration applies them between the public health paths and a final rule that refuses every
 * other request.
 */
@FunctionalInterface
public interface AccessRules {

  /** No paths beyond the public ones, for a service without an API. */
  AccessRules NONE = rules -> {};

  void apply(AuthorizeHttpRequestsConfigurer<?>.AuthorizationManagerRequestMatcherRegistry rules);
}
