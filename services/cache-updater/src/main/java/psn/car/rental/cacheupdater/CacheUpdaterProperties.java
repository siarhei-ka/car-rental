package psn.car.rental.cacheupdater;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the cache updater.
 *
 * @param administrationUrl the base address of the administration service
 * @param administrationTimeout the time limit of the snapshot request
 */
@ConfigurationProperties("cache-updater")
public record CacheUpdaterProperties(
    URI administrationUrl, @DefaultValue("PT5S") Duration administrationTimeout) {}
