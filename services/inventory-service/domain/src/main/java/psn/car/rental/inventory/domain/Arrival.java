package psn.car.rental.inventory.domain;

import java.util.Objects;

/**
 * Cars that have actually arrived at another location and must be added to its stock.
 *
 * <p>An arrival is produced when a one-way rental is returned or a transfer is completed.
 */
public record Arrival(StockKey destination, int cars) {

  public Arrival {
    Objects.requireNonNull(destination, "destination");
    if (cars < 1) {
      throw new IllegalArgumentException("An arrival brings at least 1 car, but was " + cars);
    }
  }
}
