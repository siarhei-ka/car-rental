package psn.car.rental.reservation.web;

/** Formats of identifiers in the API. */
final class Patterns {

  static final String ID = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
  static final String CODE = "[A-Z][A-Z0-9_]{0,31}";
  static final String UUID =
      "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";
  static final String IDEMPOTENCY_KEY = "[A-Za-z0-9._-]{8,128}";

  private Patterns() {}
}
