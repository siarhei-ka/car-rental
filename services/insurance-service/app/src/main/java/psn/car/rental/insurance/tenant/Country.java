package psn.car.rental.insurance.tenant;

import java.util.regex.Pattern;

/** A country where cars are rented. Its insurance prices are in its currency. */
public record Country(String code) {

  private static final Pattern CODE = Pattern.compile("[a-z]{2}");

  public Country {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new IllegalArgumentException(
          "A country code is two lowercase letters, but was " + code);
    }
  }
}
