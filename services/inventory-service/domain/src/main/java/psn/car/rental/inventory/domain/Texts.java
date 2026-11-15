package psn.car.rental.inventory.domain;

/** Validation helper for identifier values. */
final class Texts {

  private Texts() {}

  static String requireNotBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
