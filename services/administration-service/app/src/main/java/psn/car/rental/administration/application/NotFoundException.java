package psn.car.rental.administration.application;

/** The requested reference data does not exist. */
public class NotFoundException extends RuntimeException {

  public NotFoundException(String message) {
    super(message);
  }
}
