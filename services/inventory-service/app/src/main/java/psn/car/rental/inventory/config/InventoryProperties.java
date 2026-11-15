package psn.car.rental.inventory.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the inventory service.
 *
 * @param countries countries served from the start, in addition to those that already have a schema
 *     and those announced by the administration service. Usually empty
 * @param seedData whether example stock is loaded when a country's schema is created
 * @param holdTimeLimit how long a provisional hold lives without being confirmed
 * @param administrationUrl where the list of countries is loaded from at startup, or null
 */
@ConfigurationProperties("inventory")
public record InventoryProperties(
    List<String> countries,
    @DefaultValue("false") boolean seedData,
    @DefaultValue("PT2M") Duration holdTimeLimit,
    URI administrationUrl) {}
