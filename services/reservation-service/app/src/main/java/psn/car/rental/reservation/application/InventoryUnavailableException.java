package psn.car.rental.reservation.application;

/** The inventory service did not answer in time or failed. */
public class InventoryUnavailableException extends RuntimeException {

  public InventoryUnavailableException(String message) {
    super(message);
  }

  public InventoryUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
