package psn.car.rental.inventory.domain;

/** Identifies a rental location. */
public record LocationId(String value) {

  public LocationId {
    Texts.requireNotBlank(value, "LocationId");
  }
}
