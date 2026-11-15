package psn.car.rental.reservation.application;

import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.tenant.Country;

/**
 * The pricing service, which calculates the price of a rental for a customer. It also checks the
 * driver: the pricing service reads the driver profile and refuses a driver who is too young.
 */
public interface Pricing {

  /**
   * The price of a rental for the customer, calculated now.
   *
   * @throws PricingRefusedException if the pricing service refuses the rental
   * @throws PricingUnavailableException if the pricing service does not answer in time
   */
  Price quote(Country country, String customerId, RentalRequest rental);
}
