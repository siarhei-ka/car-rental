package psn.car.rental.cacheupdater;

/**
 * The layout of reference data in Redis. The info services read the same keys.
 *
 * <ul>
 *   <li>{@code ref:countries}, {@code ref:car-types} and {@code ref:car-classes} are hashes from
 *       code to JSON.
 *   <li>{@code ref:locations:<country>} is a hash from location ID to JSON.
 *   <li>{@code ref:loaded} holds the time of the last full load. Without it the cache counts as
 *       empty.
 * </ul>
 */
final class RedisKeys {

  static final String COUNTRIES = "ref:countries";
  static final String CAR_TYPES = "ref:car-types";
  static final String CAR_CLASSES = "ref:car-classes";
  static final String LOCATIONS_PREFIX = "ref:locations:";
  static final String LOADED = "ref:loaded";

  private RedisKeys() {}

  static String locations(String country) {
    return LOCATIONS_PREFIX + country;
  }
}
