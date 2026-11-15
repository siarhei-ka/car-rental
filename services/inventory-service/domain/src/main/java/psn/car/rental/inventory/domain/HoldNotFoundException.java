package psn.car.rental.inventory.domain;

/** The hold does not exist, or it was already released or completed. */
public class HoldNotFoundException extends RuntimeException {

  public HoldNotFoundException(String message) {
    super(message);
  }
}
