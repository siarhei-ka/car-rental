package psn.car.rental.reservation.domain;

import java.util.regex.Pattern;

/** An extra the customer chose, and how many. */
public record ExtraChoice(String code, int quantity) {

  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public ExtraChoice {
    if (code == null || !CODE.matcher(code).matches()) {
      throw new InvalidReservationException("An extra code is an upper-case code such as GPS");
    }
    if (quantity < 1) {
      throw new InvalidReservationException("The quantity of an extra is at least 1");
    }
  }
}
