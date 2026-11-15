package psn.car.rental.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * The entry point of one UI. It signs the user in through Keycloak, keeps the tokens in a session
 * stored in Redis, and passes API calls on to the services with the user's token.
 *
 * <p>The same application runs twice, once per UI: with the profile {@code customer} for the
 * customer UI and with {@code admin} for the admin UI. Each instance has its own Keycloak client,
 * its own cookies and only the routes of its UI.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

  public static void main(String[] args) {
    SpringApplication.run(ApiGatewayApplication.class, args);
  }
}
