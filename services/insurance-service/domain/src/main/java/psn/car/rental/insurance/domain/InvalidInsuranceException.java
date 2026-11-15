package psn.car.rental.insurance.domain;

/** An insurance option, a driver or a price that breaks a rule. */
public class InvalidInsuranceException extends RuntimeException {

  public InvalidInsuranceException(String message) {
    super(message);
  }
}
