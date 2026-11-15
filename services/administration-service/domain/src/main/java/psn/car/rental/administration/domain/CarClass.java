package psn.car.rental.administration.domain;

/**
 * A level within a car type, for example {@code ECONOMY}. A deactivated class accepts no new
 * reservations.
 */
public record CarClass(String code, String name, boolean active) {

  public CarClass {
    Rules.require(Rules.CODE, code, "Car class code");
    name = Rules.requireName(name, "Car class name");
  }
}
