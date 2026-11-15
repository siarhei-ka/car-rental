package psn.car.rental.reservation.domain;

/** The request breaks a rule of the reservation. */
public class InvalidReservationException extends RuntimeException {

  public InvalidReservationException(String message) {
    super(message);
  }
}
