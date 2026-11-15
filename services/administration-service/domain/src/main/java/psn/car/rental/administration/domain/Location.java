package psn.car.rental.administration.domain;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Objects;

/**
 * A place where cars are picked up and returned. It belongs to one country, and dates and times at
 * the location are entered and shown in its time zone.
 *
 * @param latitude of the city, from -90 to 90
 * @param longitude of the city, from -180 to 180
 */
public record Location(
    String id,
    String country,
    String name,
    String city,
    double latitude,
    double longitude,
    ZoneId timeZone,
    boolean active) {

  public Location {
    Rules.require(Rules.ID, id, "Location ID");
    Rules.require(Rules.COUNTRY_CODE, country, "Country code");
    name = Rules.requireName(name, "Location name");
    city = Rules.requireName(city, "City");
    if (!(latitude >= -90 && latitude <= 90) || !(longitude >= -180 && longitude <= 180)) {
      throw new InvalidReferenceDataException(
          "Coordinates are out of range: " + latitude + ", " + longitude);
    }
    Objects.requireNonNull(timeZone, "timeZone");
  }

  /**
   * Reads a time zone such as {@code Europe/Warsaw}.
   *
   * @throws InvalidReferenceDataException if it is not a known region
   */
  public static ZoneId timeZone(String id) {
    if (id == null || !id.contains("/")) {
      throw new InvalidReferenceDataException("Time zone must be a region such as Europe/Warsaw");
    }
    try {
      return ZoneId.of(id);
    } catch (DateTimeException e) {
      throw new InvalidReferenceDataException("Time zone is not known: " + id);
    }
  }
}
