package psn.car.rental.ancillary;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Extras, their prices and the one-way fee of each country. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class AncillaryApplication {

  public static void main(String[] args) {
    SpringApplication.run(AncillaryApplication.class, args);
  }
}
