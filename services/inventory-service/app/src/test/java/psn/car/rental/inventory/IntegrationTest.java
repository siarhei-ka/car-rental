package psn.car.rental.inventory;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import psn.car.rental.shared.security.TestTokens;

/**
 * A test against the whole application and a real PostgreSQL. All such tests share one Spring
 * context and one database, so each test uses its own location IDs.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      "inventory.countries=pl,de",
      "inventory.administration-url=http://administration.invalid",
      "inventory.seed-data=false",
      "inventory.hold-expiry-check=PT1H",
      "logging.structured.format.console="
    })
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, TestTokens.AllRolesByDefault.class})
public @interface IntegrationTest {}
