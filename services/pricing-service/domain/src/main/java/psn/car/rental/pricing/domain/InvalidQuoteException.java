package psn.car.rental.pricing.domain;

/** A request for a quote that is not valid, such as a start in the past or an unknown extra. */
public class InvalidQuoteException extends RuntimeException {

  public InvalidQuoteException(String message) {
    super(message);
  }
}
