package psn.car.rental.inventory.domain;

/** Identifies a transfer of cars between two locations. */
public record TransferId(String value) {

  public TransferId {
    Texts.requireNotBlank(value, "TransferId");
  }
}
