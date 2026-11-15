package psn.car.rental.pricing.tenant;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import psn.car.rental.pricing.config.PricingProperties;

/**
 * Creates and migrates the schema of every served country, and remembers each country's currency in
 * the table {@code served_country}, so that prices keep their currency after a restart.
 *
 * <p>At startup it serves the countries already recorded, plus any configured ones. A country
 * announced later by the administration service is added by {@link #ensure(Country, boolean,
 * String)}.
 */
@Component
public class TenantMigrations implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(TenantMigrations.class);

  private final DataSource dataSource;
  private final JdbcClient jdbc;
  private final Countries countries;
  private final PricingProperties properties;

  public TenantMigrations(
      DataSource dataSource, Countries countries, PricingProperties properties) {
    this.dataSource = dataSource;
    this.jdbc = JdbcClient.create(dataSource);
    this.countries = countries;
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    Flyway.configure()
        .dataSource(dataSource)
        .schemas("public")
        .locations("classpath:db/shared")
        .load()
        .migrate();
    jdbc.sql("select code, active, currency from served_country order by code")
        .query((row, n) ->
            Map.entry(
                row.getString("code"),
                new Countries.Served(row.getBoolean("active"), row.getString("currency"))))
        .list()
        .forEach(entry ->
            ensure(
                new Country(entry.getKey()),
                entry.getValue().active(),
                entry.getValue().currency()));
    properties
        .countries()
        .forEach((code, currency) -> ensure(new Country(code.trim()), true, currency.trim()));
  }

  /**
   * Serves a country: creates and migrates its schema if needed, and records whether it is active
   * and its currency. Calling it again only updates both.
   */
  public synchronized void ensure(Country country, boolean active, String currency) {
    String code = Currency.getInstance(currency).getCurrencyCode();
    if (!countries.isServed(country)) {
      migrate(country);
    }
    jdbc.sql(
            """
            insert into served_country (code, active, currency) values (:code, :active, :currency)
            on conflict (code) do update set active = excluded.active, currency = excluded.currency
            """)
        .param("code", country.code())
        .param("active", active)
        .param("currency", code)
        .update();
    countries.register(country, active, code);
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
