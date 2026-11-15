package psn.car.rental.pricing.persistence;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.pricing.application.RateStore;
import psn.car.rental.pricing.domain.DailyRate;
import psn.car.rental.pricing.domain.Money;
import psn.car.rental.pricing.domain.RateKey;
import psn.car.rental.pricing.tenant.Country;

/**
 * Daily rates in the schema of their country. The schema name comes from a validated country code,
 * so it is safe in SQL.
 */
@Repository
class JdbcRateStore implements RateStore {

  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcRateStore(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public List<DailyRate> list(Country country, Optional<String> locationId) {
    var statement =
        jdbc.sql(
            "select location_id, car_type, car_class, amount, currency from "
                + country.schema()
                + ".daily_rate where (cast(:location as varchar) is null or location_id = :location)"
                + " order by location_id, car_type, car_class");
    return statement
        .param("location", locationId.orElse(null))
        .query((row, n) ->
            new DailyRate(
                new RateKey(
                    row.getString("location_id"),
                    row.getString("car_type"),
                    row.getString("car_class")),
                new Money(
                    row.getBigDecimal("amount"), Currency.getInstance(row.getString("currency")))))
        .list();
  }

  @Override
  public void save(Country country, DailyRate rate) {
    jdbc.sql(
            "insert into "
                + country.schema()
                + ".daily_rate (location_id, car_type, car_class, amount, currency, updated_at)"
                + " values (:location, :carType, :carClass, :amount, :currency, :now)"
                + " on conflict (location_id, car_type, car_class) do update set"
                + " amount = excluded.amount, currency = excluded.currency,"
                + " updated_at = excluded.updated_at")
        .param("location", rate.key().locationId())
        .param("carType", rate.key().carType())
        .param("carClass", rate.key().carClass())
        .param("amount", rate.price().amount())
        .param("currency", rate.price().currency().getCurrencyCode())
        .param("now", OffsetDateTime.now(clock))
        .update();
  }
}
