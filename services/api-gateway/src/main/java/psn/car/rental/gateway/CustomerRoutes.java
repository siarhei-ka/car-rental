package psn.car.rental.gateway;

import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.POST;
import static org.springframework.web.servlet.function.RequestPredicates.PUT;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The routes of the customer UI. Reference data, search and list prices are open to every visitor.
 * The driver profile, the final price and reservations are only for a signed-in customer.
 */
@Configuration(proxyBeanMethods = false)
@Profile("customer")
class CustomerRoutes {

  static final String[] PUBLIC = {
    "/api/v1/countries/**",
    "/api/v1/locations/**",
    "/api/v1/car-types/**",
    "/api/v1/car-classes/**",
    "/api/v1/search/**",
    "/api/v1/rates/**",
    "/api/v1/extras/**",
    "/api/v1/one-way-fee",
    "/api/v1/insurance-options"
  };

  @Bean
  SecurityConfiguration.AccessRules customerAccessRules() {
    return requests ->
        requests
            .requestMatchers(HttpMethod.GET, PUBLIC)
            .permitAll()
            .requestMatchers("/api/v1/profile")
            .hasRole(Roles.CUSTOMER)
            .requestMatchers(HttpMethod.POST, "/api/v1/quotes")
            .hasRole(Roles.CUSTOMER)
            .requestMatchers("/api/v1/reservations", "/api/v1/reservations/**")
            .hasRole(Roles.CUSTOMER);
  }

  @Bean
  RouterFunction<ServerResponse> customerRouter(GatewayProperties properties, AccessTokens tokens) {
    return Proxy.to(
            tokens,
            "locations",
            properties.service("location-info"),
            GET("/api/v1/countries/**").or(GET("/api/v1/locations/**")))
        .and(
            Proxy.to(
                tokens,
                "car-types",
                properties.service("car-type-info"),
                GET("/api/v1/car-types/**").or(GET("/api/v1/car-classes/**"))))
        .and(Proxy.to(tokens, "search", properties.service("search"), GET("/api/v1/search/**")))
        .and(Proxy.to(tokens, "rates", properties.service("pricing"), GET("/api/v1/rates/**")))
        .and(
            Proxy.to(
                tokens,
                "extras",
                properties.service("ancillary"),
                GET("/api/v1/extras/**").or(GET("/api/v1/one-way-fee"))))
        .and(
            Proxy.to(
                tokens,
                "insurance",
                properties.service("insurance"),
                GET("/api/v1/insurance-options")))
        .and(
            Proxy.to(
                tokens,
                "profile",
                properties.service("customer"),
                GET("/api/v1/profile").or(PUT("/api/v1/profile"))))
        .and(Proxy.to(tokens, "quotes", properties.service("pricing"), POST("/api/v1/quotes")))
        .and(
            Proxy.to(
                tokens,
                "reservations",
                properties.service("reservation"),
                GET("/api/v1/reservations")
                    .or(POST("/api/v1/reservations"))
                    .or(GET("/api/v1/reservations/{id}"))
                    .or(POST("/api/v1/reservations/{id}/cancel"))));
  }
}
