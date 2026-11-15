package psn.car.rental.reservation.config;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the reservation service.
 *
 * @param countries countries served from the start, in addition to those that already have a schema
 *     and those announced by the administration service. Usually empty
 * @param maxDays the longest rental
 * @param inventoryUrl the base address of the inventory service
 * @param inventoryTimeout the time limit of a call to the inventory service (NFR-9)
 * @param inventoryRetryAfter how long a pending inventory update waits before it is retried
 * @param administrationUrl where the list of countries is loaded from at startup, or null
 * @param pricingUrl the base address of the pricing service
 * @param pricingTimeout the time limit of a call to the pricing service. The pricing service itself
 *     calls other services with a limit of 2 seconds each, so its limit is longer
 */
@ConfigurationProperties("reservation")
public record ReservationProperties(
    List<String> countries,
    @DefaultValue("30") int maxDays,
    URI inventoryUrl,
    @DefaultValue("PT2S") Duration inventoryTimeout,
    @DefaultValue("PT30S") Duration inventoryRetryAfter,
    URI administrationUrl,
    URI pricingUrl,
    @DefaultValue("PT5S") Duration pricingTimeout) {}
