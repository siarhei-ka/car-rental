package psn.car.rental.inventory.domain;

/** Code of a car class within a type, for example ECONOMY or PREMIUM. */
public record CarClassCode(String value) {

  public CarClassCode {
    Texts.requireNotBlank(value, "CarClassCode");
  }
}
