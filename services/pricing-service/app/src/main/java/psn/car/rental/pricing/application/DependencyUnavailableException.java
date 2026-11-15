package psn.car.rental.pricing.application;

/** A service the price depends on did not answer in time, or failed. No price is invented. */
public class DependencyUnavailableException extends RuntimeException {

  public DependencyUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
