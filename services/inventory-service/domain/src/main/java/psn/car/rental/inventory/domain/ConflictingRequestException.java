package psn.car.rental.inventory.domain;

/** An ID was reused for a request with different details. */
public class ConflictingRequestException extends RuntimeException {

  public ConflictingRequestException(String message) {
    super(message);
  }
}
