package psn.car.rental.administration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Owns reference data: countries, locations, car types and car classes. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AdministrationApplication {

  public static void main(String[] args) {
    SpringApplication.run(AdministrationApplication.class, args);
  }
}
