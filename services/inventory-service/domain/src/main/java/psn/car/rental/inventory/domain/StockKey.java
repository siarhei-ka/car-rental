package psn.car.rental.inventory.domain;

import java.util.Objects;

/** Stock is counted per location, car type and car class. */
public record StockKey(LocationId location, CarTypeCode carType, CarClassCode carClass) {

  public StockKey {
    Objects.requireNonNull(location, "location");
    Objects.requireNonNull(carType, "carType");
    Objects.requireNonNull(carClass, "carClass");
  }

  /** The same car type and class at another location. */
  public StockKey at(LocationId otherLocation) {
    return new StockKey(otherLocation, carType, carClass);
  }
}
