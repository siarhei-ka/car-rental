package psn.car.rental.administration.tenant;

/** Names of the database schemas. Country codes are validated before they reach this class. */
public final class Schemas {

  /** Reference data shared by all countries, and the outbox. */
  public static final String SHARED = "shared";

  private Schemas() {}

  public static String ofCountry(String code) {
    if (code == null || !code.matches("[a-z]{2}")) {
      throw new IllegalArgumentException("A country code is two lowercase letters: " + code);
    }
    return "tenant_" + code;
  }
}
