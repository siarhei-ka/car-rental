package psn.car.rental.info.location;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads reference data from Redis, which the cache updater keeps up to date. This service never
 * reads a database. The key layout is described in the architecture document.
 */
@Component
class ReferenceReader {

  static final String LOADED = "ref:loaded";

  private final StringRedisTemplate redis;
  private final JsonMapper json;

  ReferenceReader(StringRedisTemplate redis, JsonMapper json) {
    this.redis = redis;
    this.json = json;
  }

  /** All items of a hash, ordered by the given field. */
  List<Map<String, Object>> all(String key, String orderBy) {
    requireLoaded();
    return guarded(() ->
        redis.<String, String>opsForHash().values(key).stream()
            .map(this::parse)
            .sorted(Comparator.comparing(item -> String.valueOf(item.get(orderBy))))
            .toList());
  }

  Optional<Map<String, Object>> one(String key, String field) {
    requireLoaded();
    return guarded(() ->
        Optional.ofNullable(redis.<String, String>opsForHash().get(key, field)).map(this::parse));
  }

  private void requireLoaded() {
    if (!Boolean.TRUE.equals(guarded(() -> redis.hasKey(LOADED)))) {
      throw new CacheUnavailableException("Reference data is not loaded yet");
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String value) {
    return json.readValue(value, Map.class);
  }

  private static <T> T guarded(java.util.function.Supplier<T> read) {
    try {
      return read.get();
    } catch (DataAccessException e) {
      throw new CacheUnavailableException("Reference data cannot be read: " + e.getMessage());
    }
  }

  static boolean active(Map<String, Object> item) {
    return Boolean.TRUE.equals(item.get("active"));
  }
}
