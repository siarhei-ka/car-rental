package psn.car.rental.reservation.domain;

/** The reservation is in a state that does not allow the change. */
public class InvalidStateException extends RuntimeException {

  public InvalidStateException(String message) {
    super(message);
  }
}
