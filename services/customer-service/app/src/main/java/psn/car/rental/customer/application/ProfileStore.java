package psn.car.rental.customer.application;

import java.util.Optional;
import psn.car.rental.customer.domain.CustomerId;
import psn.car.rental.customer.domain.DriverProfile;

/** Where driver profiles are kept. */
public interface ProfileStore {

  Optional<DriverProfile> find(CustomerId customer);

  /** Creates the profile, or replaces the customer's existing one. */
  void save(DriverProfile profile);
}
