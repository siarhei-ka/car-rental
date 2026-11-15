package psn.car.rental.reservation.application;

import psn.car.rental.reservation.domain.Price;

/**
 * The price is no longer the one the customer saw, for example because a rate changed or a discount
 * ended. No reservation is made; the customer sees the new price and confirms again.
 */
public class PriceChangedException extends RuntimeException {

  private final transient Price price;

  public PriceChangedException(Price price) {
    super("The price has changed to " + price.total().toPlainString() + " " + price.currency());
    this.price = price;
  }

  /** The new price. */
  public Price price() {
    return price;
  }
}
