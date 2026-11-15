package psn.car.rental.reservation.application;

/** The pricing service did not answer in time, so no reservation is made. */
public class PricingUnavailableException extends RuntimeException {

  public PricingUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
