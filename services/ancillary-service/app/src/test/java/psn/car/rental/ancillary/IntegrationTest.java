package psn.car.rental.ancillary;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import psn.car.rental.shared.security.TestTokens;

/**
 * A test against the whole application, a real PostgreSQL and a real Kafka. All such tests share
 * one Spring context and one database, so each test uses its own location IDs or codes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      "ancillary.countries.pl=PLN",
      "ancillary.countries.de=EUR",
      "ancillary.administration-url=http://administration.invalid",
      "ancillary.seed-data=false",
      "logging.structured.format.console="
    })
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, TestTokens.AllRolesByDefault.class})
public @interface IntegrationTest {}
