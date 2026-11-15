package psn.car.rental.administration.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the administration service.
 *
 * @param seedData whether example reference data is loaded
 * @param referenceDataTopic the Kafka topic of reference data events
 */
@ConfigurationProperties("administration")
public record AdministrationProperties(
    @DefaultValue("false") boolean seedData,
    @DefaultValue("reference-data") String referenceDataTopic) {}
