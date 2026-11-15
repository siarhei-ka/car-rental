package psn.car.rental.cacheupdater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Loads the full snapshot at startup, and again whenever Redis has lost its data, for example after
 * a restart without persistence. A failed load is retried on the next check.
 */
@Component
public class SnapshotLoader {

  private static final Logger log = LoggerFactory.getLogger(SnapshotLoader.class);

  private final SnapshotSource source;
  private final ReferenceCache cache;

  public SnapshotLoader(SnapshotSource source, ReferenceCache cache) {
    this.source = source;
    this.cache = cache;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void loadAtStartup() {
    load();
  }

  /**
   * Loads the snapshot if the cache is empty.
   *
   * @return true if a snapshot was loaded
   */
  @Scheduled(
      initialDelayString = "${cache-updater.reload-check:PT30S}",
      fixedDelayString = "${cache-updater.reload-check:PT30S}")
  public boolean reloadIfEmpty() {
    try {
      return !cache.isLoaded() && load();
    } catch (RuntimeException e) {
      log.warn("Redis is not reachable: {}", e.getMessage());
      return false;
    }
  }

  private boolean load() {
    try {
      cache.replaceAll(source.load());
      return true;
    } catch (RuntimeException e) {
      log.warn("Reference data could not be loaded and will be retried: {}", e.getMessage());
      return false;
    }
  }
}
