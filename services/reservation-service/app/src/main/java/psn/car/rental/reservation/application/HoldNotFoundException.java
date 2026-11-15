package psn.car.rental.reservation.application;

/** The inventory has no hold for the reservation, for example because it expired. */
public class HoldNotFoundException extends RuntimeException {

  public HoldNotFoundException(String message) {
    super(message);
  }

  public HoldNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
