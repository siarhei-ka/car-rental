package psn.car.rental.discount;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Discount rules of each country, and the best discount for a basket. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DiscountApplication {

  public static void main(String[] args) {
    SpringApplication.run(DiscountApplication.class, args);
  }
}
