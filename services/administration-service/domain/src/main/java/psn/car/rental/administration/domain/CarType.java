package psn.car.rental.administration.domain;

/**
 * A category of car, for example {@code SEDAN}. Types are data, so a new one needs no code change.
 * A deactivated type accepts no new reservations.
 */
public record CarType(String code, String name, boolean active) {

  public CarType {
    Rules.require(Rules.CODE, code, "Car type code");
    name = Rules.requireName(name, "Car type name");
  }
}
