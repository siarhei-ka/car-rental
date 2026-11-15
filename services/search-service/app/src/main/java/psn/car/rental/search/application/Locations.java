package psn.car.rental.search.application;

import java.util.Optional;
import psn.car.rental.search.domain.Location;

/** The location info service. */
public interface Locations {

  /** A location of a country, or empty if it does not exist. */
  Optional<Location> find(String country, String id);
}
