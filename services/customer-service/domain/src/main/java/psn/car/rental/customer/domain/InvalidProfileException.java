package psn.car.rental.customer.domain;

/** A driver profile that breaks a rule. The message says which one, without personal data. */
public class InvalidProfileException extends RuntimeException {

  public InvalidProfileException(String message) {
    super(message);
  }
}
