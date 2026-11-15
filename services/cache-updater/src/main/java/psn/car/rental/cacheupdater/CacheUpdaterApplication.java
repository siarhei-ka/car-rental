package psn.car.rental.cacheupdater;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Keeps the Redis copy of reference data up to date. */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class CacheUpdaterApplication {

  public static void main(String[] args) {
    SpringApplication.run(CacheUpdaterApplication.class, args);
  }
}
