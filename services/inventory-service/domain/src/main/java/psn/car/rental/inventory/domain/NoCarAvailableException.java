package psn.car.rental.inventory.domain;

/** No car is free for the requested period. */
public class NoCarAvailableException extends RuntimeException {

  public NoCarAvailableException(String message) {
    super(message);
  }
}
