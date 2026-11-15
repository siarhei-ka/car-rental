package psn.car.rental.cacheupdater;

import java.time.Clock;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Writes reference data to Redis. */
@Component
public class ReferenceCache {

  private static final Logger log = LoggerFactory.getLogger(ReferenceCache.class);

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final Clock clock;

  public ReferenceCache(StringRedisTemplate redis, JsonMapper json, Clock clock) {
    this.redis = redis;
    this.json = json;
    this.clock = clock;
  }

  public boolean isLoaded() {
    return Boolean.TRUE.equals(redis.hasKey(RedisKeys.LOADED));
  }

  /**
   * Applies one reference data event. An event of an unknown kind is skipped.
   *
   * @return false if the event was skipped
   */
  public boolean apply(Map<String, Object> event) {
    Object kind = event.get("kind");
    if (!(event.get("data") instanceof Map<?, ?> data)) {
      log.warn("Skipped a reference data event without data: {}", event.get("eventId"));
      return false;
    }
    String key =
        switch (String.valueOf(kind)) {
          case "COUNTRY" -> RedisKeys.COUNTRIES;
          case "CAR_TYPE" -> RedisKeys.CAR_TYPES;
          case "CAR_CLASS" -> RedisKeys.CAR_CLASSES;
          case "LOCATION" -> RedisKeys.locations(String.valueOf(event.get("country")));
          default -> null;
        };
    if (key == null) {
      log.warn("Skipped a reference data event of unknown kind {}", kind);
      return false;
    }
    String field = String.valueOf(data.get("LOCATION".equals(kind) ? "id" : "code"));
    redis.opsForHash().put(key, field, json.writeValueAsString(data));
    return true;
  }

  /**
   * Replaces all reference data in one Redis transaction, so that readers never see a half-done
   * load, and marks the cache as loaded.
   */
  public void replaceAll(Snapshot snapshot) {
    Set<String> oldLocationKeys = new HashSet<>();
    try (var cursor =
        redis.scan(ScanOptions.scanOptions().match(RedisKeys.LOCATIONS_PREFIX + "*").build())) {
      cursor.forEachRemaining(oldLocationKeys::add);
    }
    Map<String, Map<String, String>> hashes = new LinkedHashMap<>();
    hashes.put(RedisKeys.COUNTRIES, byField(snapshot.countries(), "code"));
    hashes.put(RedisKeys.CAR_TYPES, byField(snapshot.carTypes(), "code"));
    hashes.put(RedisKeys.CAR_CLASSES, byField(snapshot.carClasses(), "code"));
    for (Map<String, Object> location : snapshot.locations()) {
      hashes
          .computeIfAbsent(
              RedisKeys.locations(String.valueOf(location.get("country"))),
              k -> new LinkedHashMap<>())
          .put(String.valueOf(location.get("id")), json.writeValueAsString(location));
    }
    redis.execute(
        new SessionCallback<List<Object>>() {
          @Override
          @SuppressWarnings({"unchecked", "rawtypes"})
          public List<Object> execute(RedisOperations operations) throws DataAccessException {
            operations.multi();
            operations.delete(oldLocationKeys);
            hashes.keySet().forEach(operations::delete);
            hashes.forEach((key, fields) -> {
              if (!fields.isEmpty()) {
                operations.opsForHash().putAll(key, fields);
              }
            });
            operations.opsForValue().set(RedisKeys.LOADED, clock.instant().toString());
            return operations.exec();
          }
        });
    log.info(
        "Loaded {} countries, {} car types, {} car classes and {} locations into Redis",
        snapshot.countries().size(),
        snapshot.carTypes().size(),
        snapshot.carClasses().size(),
        snapshot.locations().size());
  }

  private Map<String, String> byField(List<Map<String, Object>> items, String field) {
    Map<String, String> result = new LinkedHashMap<>();
    items.forEach(
        item -> result.put(String.valueOf(item.get(field)), json.writeValueAsString(item)));
    return result;
  }

  /** All reference data, as returned by the administration service. */
  public record Snapshot(
      List<Map<String, Object>> countries,
      List<Map<String, Object>> carTypes,
      List<Map<String, Object>> carClasses,
      List<Map<String, Object>> locations) {}
}
