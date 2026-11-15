package psn.car.rental.insurance.application;

/** The insurance provider gave no price in time. No price is invented in its place. */
public class ProviderUnavailableException extends RuntimeException {

  public ProviderUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
