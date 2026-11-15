package psn.car.rental.customer.application;

import java.time.Clock;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import psn.car.rental.customer.domain.CustomerId;
import psn.car.rental.customer.domain.DriverProfile;
import psn.car.rental.customer.domain.LicenceCountry;

/** Reads and saves the driver profile of the signed-in customer. */
@Service
public class ProfileService {

  private final ProfileStore store;
  private final Clock clock;

  public ProfileService(ProfileStore store, Clock clock) {
    this.store = store;
    this.clock = clock;
  }

  public DriverProfile get(CustomerId customer) {
    return store.find(customer).orElseThrow(ProfileNotFoundException::new);
  }

  public DriverProfile save(CustomerId customer, LocalDate dateOfBirth, LicenceCountry country) {
    DriverProfile profile =
        DriverProfile.enter(customer, dateOfBirth, country, LocalDate.now(clock));
    store.save(profile);
    return profile;
  }
}
