package psn.car.rental.inventory.domain;

/** The stock cannot be reduced, because holds or transfers rely on the cars. */
public class StockInUseException extends RuntimeException {

  public StockInUseException(String message) {
    super(message);
  }
}
