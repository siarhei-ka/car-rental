package psn.car.rental.insurance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Insurance options and their prices, which come from the insurance provider. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class InsuranceApplication {

  public static void main(String[] args) {
    SpringApplication.run(InsuranceApplication.class, args);
  }
}
