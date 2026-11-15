package psn.car.rental.gateway;

import static org.springframework.web.servlet.function.RequestPredicates.path;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * The routes of the admin UI. Only administrators may use them. Countries, car types and car
 * classes are changed only by a super administrator. The data of one country is handled by the
 * {@link Tenant} filter.
 */
@Configuration(proxyBeanMethods = false)
@Profile("admin")
class AdminRoutes {

  static final String[] SHARED_DATA = {
    "/api/v1/admin/countries/**", "/api/v1/admin/car-types/**", "/api/v1/admin/car-classes/**"
  };

  @Bean
  SecurityConfiguration.AccessRules adminAccessRules() {
    return requests ->
        requests
            .requestMatchers(HttpMethod.GET, SHARED_DATA)
            .hasAnyRole(Roles.COUNTRY_ADMIN, Roles.SUPER_ADMIN)
            .requestMatchers(SHARED_DATA)
            .hasRole(Roles.SUPER_ADMIN)
            .requestMatchers("/api/v1/admin/**")
            .hasAnyRole(Roles.COUNTRY_ADMIN, Roles.SUPER_ADMIN);
  }

  @Bean
  RouterFunction<ServerResponse> adminRouter(GatewayProperties properties, AccessTokens tokens) {
    var administration = properties.service("administration");
    var inventory = properties.service("inventory");
    var pricing = properties.service("pricing");
    var ancillary = properties.service("ancillary");
    var discount = properties.service("discount");
    var reservation = properties.service("reservation");
    return Proxy.to(
            tokens,
            "admin-stock",
            inventory,
            path("/api/v1/admin/stock/**").or(path("/api/v1/admin/transfers/**")),
            Tenant.filter())
        .and(
            Proxy.to(
                tokens,
                "admin-locations",
                administration,
                path("/api/v1/admin/locations/**"),
                Tenant.filter()))
        .and(
            Proxy.to(
                tokens,
                "admin-shared-data",
                administration,
                path(SHARED_DATA[0]).or(path(SHARED_DATA[1])).or(path(SHARED_DATA[2]))))
        .and(
            Proxy.to(
                tokens, "admin-rates", pricing, path("/api/v1/admin/rates/**"), Tenant.filter()))
        .and(
            Proxy.to(
                tokens,
                "admin-extras",
                ancillary,
                path("/api/v1/admin/extras/**").or(path("/api/v1/admin/one-way-fee")),
                Tenant.filter()))
        .and(
            Proxy.to(
                tokens,
                "admin-discount-rules",
                discount,
                path("/api/v1/admin/discount-rules/**"),
                Tenant.filter()))
        .and(
            Proxy.to(
                tokens,
                "admin-reservations",
                reservation,
                path("/api/v1/admin/reservations/**"),
                Tenant.filter()));
  }
}
