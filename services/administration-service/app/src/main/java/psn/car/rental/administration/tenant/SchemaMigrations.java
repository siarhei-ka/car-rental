package psn.car.rental.administration.tenant;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import psn.car.rental.administration.config.AdministrationProperties;

/**
 * Migrates the shared schema, then the schema of every country stored in it. A country created
 * later through the API gets its schema from {@link #migrateCountry(String)}.
 */
@Component
public class SchemaMigrations implements InitializingBean {

  private static final Logger log = LoggerFactory.getLogger(SchemaMigrations.class);

  private final DataSource dataSource;
  private final boolean seedData;

  public SchemaMigrations(DataSource dataSource, AdministrationProperties properties) {
    this.dataSource = dataSource;
    this.seedData = properties.seedData();
  }

  @Override
  public void afterPropertiesSet() {
    migrate(Schemas.SHARED, "classpath:db/shared", "classpath:db/seed/shared");
    JdbcClient.create(dataSource)
        .sql("select code from shared.country order by code")
        .query(String.class)
        .list()
        .forEach(this::migrateCountry);
  }

  /** Creates and migrates the schema of a country. Running it again changes nothing. */
  public void migrateCountry(String code) {
    migrate(Schemas.ofCountry(code), "classpath:db/tenant", "classpath:db/seed/tenant/" + code);
  }

  private void migrate(String schema, String migrations, String seed) {
    List<String> locations = new ArrayList<>(List.of(migrations));
    if (seedData) {
      locations.add(seed);
    }
    var result =
        Flyway.configure()
            .dataSource(dataSource)
            .schemas(schema)
            .createSchemas(true)
            .locations(locations.toArray(String[]::new))
            .load()
            .migrate();
    log.info(
        "Schema {} is at version {} after {} migrations",
        schema,
        result.targetSchemaVersion,
        result.migrationsExecuted);
  }
}
