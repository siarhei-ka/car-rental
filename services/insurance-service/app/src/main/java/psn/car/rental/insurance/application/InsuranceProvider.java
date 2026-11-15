package psn.car.rental.insurance.application;

import java.util.Optional;
import psn.car.rental.insurance.domain.Driver;
import psn.car.rental.insurance.domain.Money;

/**
 * The third-party insurance pricing service. The system reaches it only through this interface, so
 * that a real provider can replace the mock.
 */
public interface InsuranceProvider {

  /**
   * The price per day of an insurance option in a country.
   *
   * @param driver the driver, or empty for the base price that a visitor sees as "from"
   * @throws ProviderUnavailableException if the provider gives no price in time
   */
  Money pricePerDay(String option, String rentalCountry, String currency, Optional<Driver> driver);
}
