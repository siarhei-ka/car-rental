package psn.car.rental.inventory.domain;

/** The transfer does not exist, or it was already completed. */
public class TransferNotFoundException extends RuntimeException {

  public TransferNotFoundException(String message) {
    super(message);
  }
}
