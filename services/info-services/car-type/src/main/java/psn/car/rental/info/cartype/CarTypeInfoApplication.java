package psn.car.rental.info.cartype;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Serves car types and car classes from Redis. */
@SpringBootApplication
public class CarTypeInfoApplication {

  public static void main(String[] args) {
    SpringApplication.run(CarTypeInfoApplication.class, args);
  }
}
