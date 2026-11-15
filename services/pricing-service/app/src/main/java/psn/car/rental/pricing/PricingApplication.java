package psn.car.rental.pricing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Daily rental rates, public list prices and the final price. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class PricingApplication {

  public static void main(String[] args) {
    SpringApplication.run(PricingApplication.class, args);
  }
}
