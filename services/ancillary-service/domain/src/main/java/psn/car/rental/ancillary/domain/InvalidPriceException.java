package psn.car.rental.ancillary.domain;

/** An extra, a price or a quantity that breaks a rule. */
public class InvalidPriceException extends RuntimeException {

  public InvalidPriceException(String message) {
    super(message);
  }
}
