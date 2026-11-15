package psn.car.rental.inventory.tenant;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import psn.car.rental.inventory.config.InventoryProperties;

/**
 * Creates and migrates the schema of every served country.
 *
 * <p>At startup it serves the countries that already have a schema, plus any configured ones. A
 * country announced later by the administration service gets its schema from {@link
 * #ensure(Country, boolean)}.
 */
@Component
public class TenantMigrations implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(TenantMigrations.class);

  private final DataSource dataSource;
  private final Countries countries;
  private final InventoryProperties properties;

  public TenantMigrations(
      DataSource dataSource, Countries countries, InventoryProperties properties) {
    this.dataSource = dataSource;
    this.countries = countries;
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    // Tables shared by all countries, such as the outbox, live in the public schema.
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("public")
        .locations("classpath:db/shared")
        .load()
        .migrate();
    Set<String> codes = new LinkedHashSet<>();
    if (properties.countries() != null) {
      properties.countries().stream()
          .map(String::trim)
          .filter(c -> !c.isEmpty())
          .forEach(codes::add);
    }
    codes.addAll(
        JdbcClient.create(dataSource)
            .sql(
                "select substring(schema_name from 8) from information_schema.schemata"
                    + " where schema_name ~ '^tenant_[a-z]{2}$' order by schema_name")
            .query(String.class)
            .list());
    codes.forEach(code -> ensure(new Country(code), true));
  }

  /**
   * Serves a country: creates and migrates its schema if needed, and records whether it is active.
   * Calling it again only updates the active flag.
   */
  public synchronized void ensure(Country country, boolean active) {
    if (!countries.isServed(country)) {
      migrate(country);
    }
    countries.register(country, active);
  }

  private void migrate(Country country) {
    List<String> locations = new ArrayList<>(List.of("classpath:db/migration"));
    if (properties.seedData()) {
      locations.add("classpath:db/seed/" + country.code());
    }
    var result =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(country.schema())
            .createSchemas(true)
            .locations(locations.toArray(String[]::new))
            .load()
            .migrate();
    log.info(
        "Schema {} is at version {} after {} migrations",
        country.schema(),
        result.targetSchemaVersion,
        result.migrationsExecuted);
  }
}
