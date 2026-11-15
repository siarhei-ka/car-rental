package psn.car.rental.pricing.domain;

/** A price that breaks a rule. */
public class InvalidPriceException extends RuntimeException {

  public InvalidPriceException(String message) {
    super(message);
  }
}
