package psn.car.rental.info.location;

/** Redis cannot be reached, or the cache updater has not loaded the reference data yet. */
class CacheUnavailableException extends RuntimeException {

  CacheUnavailableException(String message) {
    super(message);
  }
}
