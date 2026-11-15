package psn.car.rental.inventory.domain;

/** Code of a car type, for example SEDAN, SUV or VAN. Car types are data, not an enum. */
public record CarTypeCode(String value) {

  public CarTypeCode {
    Texts.requireNotBlank(value, "CarTypeCode");
  }
}
