package psn.car.rental.reservation.application;

/** An idempotency key or reservation ID was reused for a different request. */
public class ConflictingRequestException extends RuntimeException {

  public ConflictingRequestException(String message) {
    super(message);
  }

  public ConflictingRequestException(String message, Throwable cause) {
    super(message, cause);
  }
}
