package psn.car.rental.search.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What a visitor searches for: a pick-up and a return location of one country, a start, a number of
 * days, and optionally a car type and a car class.
 */
public record SearchQuery(
    String country,
    String pickUpLocationId,
    String returnLocationId,
    Instant start,
    int days,
    Optional<String> carType,
    Optional<String> carClass) {

  private static final Pattern COUNTRY = Pattern.compile("[a-z]{2}");
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
  private static final Pattern CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,31}");

  public SearchQuery {
    require(COUNTRY, country, "country");
    require(ID, pickUpLocationId, "pickUpLocationId");
    require(ID, returnLocationId, "returnLocationId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(carType, "carType");
    Objects.requireNonNull(carClass, "carClass");
    carType.ifPresent(code -> require(CODE, code, "carType"));
    carClass.ifPresent(code -> require(CODE, code, "carClass"));
  }

  /** The first moment that is no longer part of the rental. One day is 24 hours. */
  public Instant end() {
    return start.plus(Duration.ofDays(days));
  }

  /**
   * Checks the same limits as a reservation, so that a search never offers what cannot be reserved.
   *
   * @throws InvalidSearchException if the start is not in the future or the length is out of range
   */
  public void checkLimits(Instant now, int maxDays) {
    if (!start.isAfter(now)) {
      throw new InvalidSearchException("The start must be in the future");
    }
    if (days < 1 || days > maxDays) {
      throw new InvalidSearchException(
          "A rental lasts from 1 to " + maxDays + " days, but was " + days);
    }
  }

  private static void require(Pattern pattern, String value, String name) {
    if (value == null || !pattern.matcher(value).matches()) {
      throw new InvalidSearchException(name + " is not valid: " + value);
    }
  }
}
