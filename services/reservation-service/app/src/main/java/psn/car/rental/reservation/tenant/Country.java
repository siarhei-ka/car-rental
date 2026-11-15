package psn.car.rental.reservation.tenant;

import java.util.regex.Pattern;

/** A country is a tenant. Its data lives in its own database schema. */
public record Country(String code) {

  private static final Pattern CODE = Pattern.compile("[a-z]{2}");

  public Country {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException(
          "A country code is two lowercase letters, but was " + code);
    }
  }

  /** The database schema of this country. Safe to use in SQL because the code is validated. */
  public String schema() {
    return "tenant_" + code;
  }
}
