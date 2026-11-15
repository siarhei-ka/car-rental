package psn.car.rental.administration.domain;

/** A value of reference data breaks a rule. */
public class InvalidReferenceDataException extends RuntimeException {

  public InvalidReferenceDataException(String message) {
    super(message);
  }
}
