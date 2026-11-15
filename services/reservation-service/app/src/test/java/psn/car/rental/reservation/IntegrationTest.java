package psn.car.rental.reservation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import psn.car.rental.shared.security.TestTokens;

/**
 * A test against the whole application and a real PostgreSQL, with the inventory service replaced
 * by {@link FakeInventory}. All such tests share one Spring context and one database.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(
    properties = {
      "reservation.countries=pl,de",
      "reservation.administration-url=http://administration.invalid",
      "reservation.inventory-url=http://inventory.invalid",
      "reservation.inventory-sync-check=PT1H",
      "logging.structured.format.console="
    })
@AutoConfigureMockMvc
@Import({TestInfrastructure.class, TestTokens.AllRolesByDefault.class})
public @interface IntegrationTest {}
