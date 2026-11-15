package psn.car.rental.administration.domain;

import java.util.regex.Pattern;

/** Formats shared by all reference data. */
final class Rules {

  static final Pattern COUNTRY_CODE = Pattern.compile("[a-z]{2}");
  static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  private Rules() {}

  static String require(Pattern pattern, String value, String name) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw new InvalidReferenceDataException(name + " is not valid: " + value);
    }
    return value;
  }

  static String requireName(String value, String name) {
    if (value == null || value.isBlank() || value.length() > 100) {
      throw new InvalidReferenceDataException(name + " must have 1 to 100 characters");
    }
    return value.strip();
  }
}
