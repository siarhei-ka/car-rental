package psn.car.rental.inventory.domain;

/** Identifies a hold. It equals the ID of the reservation the hold belongs to. */
public record HoldId(String value) {

  public HoldId {
    Texts.requireNotBlank(value, "HoldId");
  }
}
