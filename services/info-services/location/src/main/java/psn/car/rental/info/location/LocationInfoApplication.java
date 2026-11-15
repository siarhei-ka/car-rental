package psn.car.rental.info.location;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Serves countries and locations from Redis. */
@SpringBootApplication
public class LocationInfoApplication {

  public static void main(String[] args) {
    SpringApplication.run(LocationInfoApplication.class, args);
  }
}
