package psn.car.rental.reservation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * What a customer asks for: a car of a type and class, from a pick-up location to a return
 * location, from a start for a number of days, with an insurance option and extras.
 *
 * @param insurance the code of the insurance option
 * @param extras the chosen extras, ordered by code
 */
public record RentalRequest(
    String pickUpLocationId,
    String returnLocationId,
    String carType,
    String carClass,
    Instant start,
    int days,
    String insurance,
    List<ExtraChoice> extras) {

  /** The insurance option that is preselected for every reservation. */
  public static final String BASIC_COVER = "BASIC";

  /** A rental with basic cover and no extras. */
  public RentalRequest(
      String pickUpLocationId,
      String returnLocationId,
      String carType,
      String carClass,
      Instant start,
      int days) {
    this(
        pickUpLocationId, returnLocationId, carType, carClass, start, days, BASIC_COVER, List.of());
  }

  private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public RentalRequest {
    require(ID, pickUpLocationId, "pickUpLocationId");
    require(ID, returnLocationId, "returnLocationId");
    require(CODE, carType, "carType");
    require(CODE, carClass, "carClass");
    Objects.requireNonNull(start, "start");
    require(CODE, insurance, "insurance");
    extras =
        extras == null
            ? List.of()
            : extras.stream().sorted(Comparator.comparing(ExtraChoice::code)).toList();
    if (new HashSet<>(extras.stream().map(ExtraChoice::code).toList()).size() != extras.size()) {
      throw new InvalidReservationException("Each extra is listed once");
    }
  }

  /** The first moment that is no longer part of the rental. One day is 24 hours. */
  public Instant end() {
    return start.plus(Duration.ofDays(days));
  }

  public boolean oneWay() {
    return !pickUpLocationId.equals(returnLocationId);
  }

  private static void require(Pattern pattern, String value, String name) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw new InvalidReservationException(name + " is not valid: " + value);
    }
  }
}
