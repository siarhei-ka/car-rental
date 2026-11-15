package psn.car.rental.reservation.application;

/** No car is free for the requested rental. */
public class NoCarAvailableException extends RuntimeException {

  public NoCarAvailableException(String message) {
    super(message);
  }

  public NoCarAvailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
