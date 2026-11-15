package psn.car.rental.search;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the search service.
 *
 * @param pricingUrl the pricing service, which owns the daily list prices
 * @param maxDays the longest rental, the same limit as the reservation service
 * @param timeout the time limit of each call to another service (NFR-9)
 */
@ConfigurationProperties("search")
public record SearchProperties(
    URI locationInfoUrl,
    URI carTypeInfoUrl,
    URI inventoryUrl,
    URI pricingUrl,
    @DefaultValue("30") int maxDays,
    @DefaultValue("PT2S") Duration timeout) {}
