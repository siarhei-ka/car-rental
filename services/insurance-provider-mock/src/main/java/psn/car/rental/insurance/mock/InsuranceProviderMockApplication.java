package psn.car.rental.insurance.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Stands in for the third-party insurance pricing service. A real provider would replace it without
 * a change to the system, so it shares no code with the system and has its own API key instead of
 * the system's tokens.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class InsuranceProviderMockApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsuranceProviderMockApplication.class, args);
  }
}
