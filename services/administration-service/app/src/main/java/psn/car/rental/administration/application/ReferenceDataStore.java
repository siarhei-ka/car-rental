package psn.car.rental.administration.application;

import java.util.List;
import java.util.Optional;
import psn.car.rental.administration.domain.CarClass;
import psn.car.rental.administration.domain.CarType;
import psn.car.rental.administration.domain.Country;
import psn.car.rental.administration.domain.Location;

/** Storage of reference data. Every method runs inside the caller's transaction. */
public interface ReferenceDataStore {

  List<Country> countries();

  Optional<Country> country(String code);

  void saveCountry(Country country);

  List<CarType> carTypes();

  Optional<CarType> carType(String code);

  void saveCarType(CarType carType);

  List<CarClass> carClasses();

  Optional<CarClass> carClass(String code);

  void saveCarClass(CarClass carClass);

  /** The locations of a country, ordered by ID. */
  List<Location> locations(String country);

  Optional<Location> location(String country, String id);

  void saveLocation(Location location);
}
