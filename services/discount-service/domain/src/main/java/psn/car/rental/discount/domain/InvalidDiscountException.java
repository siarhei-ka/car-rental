package psn.car.rental.discount.domain;

/** A discount rule, a basket or an amount breaks a rule of the domain. */
public class InvalidDiscountException extends RuntimeException {

  public InvalidDiscountException(String message) {
    super(message);
  }
}
