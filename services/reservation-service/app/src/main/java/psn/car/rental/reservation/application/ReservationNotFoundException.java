package psn.car.rental.reservation.application;

/** The reservation does not exist in this country. */
public class ReservationNotFoundException extends RuntimeException {

  public ReservationNotFoundException(String message) {
    super(message);
  }

  public ReservationNotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
