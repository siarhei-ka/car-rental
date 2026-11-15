package psn.car.rental.ancillary.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.ancillary.application.AncillaryStore;
import psn.car.rental.ancillary.domain.Charge;
import psn.car.rental.ancillary.domain.Extra;
import psn.car.rental.ancillary.domain.ExtraKind;
import psn.car.rental.ancillary.domain.Money;
import psn.car.rental.ancillary.tenant.Country;

/**
 * Extras and the one-way fee in the schema of their country. The schema name comes from a validated
 * country code, so it is safe in SQL.
 */
@Repository
class JdbcAncillaryStore implements AncillaryStore {

  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcAncillaryStore(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public List<Extra> extras(Country country, boolean activeOnly) {
    return jdbc.sql(
            "select code, name, kind, charge, amount, currency, max_quantity, active from "
                + country.schema()
                + ".extra where active or not :activeOnly order by name, code")
        .param("activeOnly", activeOnly)
        .query((row, n) -> extra(row))
        .list();
  }

  @Override
  public void save(Country country, Extra extra) {
    jdbc.sql(
            "insert into "
                + country.schema()
                + ".extra (code, name, kind, charge, amount, currency, max_quantity, active,"
                + " updated_at) values (:code, :name, :kind, :charge, :amount, :currency,"
                + " :maxQuantity, :active, :now) on conflict (code) do update set"
                + " name = excluded.name, kind = excluded.kind, charge = excluded.charge,"
                + " amount = excluded.amount, currency = excluded.currency,"
                + " max_quantity = excluded.max_quantity, active = excluded.active,"
                + " updated_at = excluded.updated_at")
        .param("code", extra.code())
        .param("name", extra.name())
        .param("kind", extra.kind().name())
        .param("charge", extra.charge().name())
        .param("amount", extra.price().amount())
        .param("currency", extra.price().currency().getCurrencyCode())
        .param("maxQuantity", extra.maxQuantity())
        .param("active", extra.active())
        .param("now", OffsetDateTime.now(clock))
        .update();
  }

  @Override
  public Optional<Money> oneWayFee(Country country) {
    return jdbc.sql("select amount, currency from " + country.schema() + ".one_way_fee")
        .query((row, n) -> money(row))
        .optional();
  }

  @Override
  public void saveOneWayFee(Country country, Money fee) {
    jdbc.sql(
            "insert into "
                + country.schema()
                + ".one_way_fee (id, amount, currency, updated_at) values (true, :amount,"
                + " :currency, :now) on conflict (id) do update set amount = excluded.amount,"
                + " currency = excluded.currency, updated_at = excluded.updated_at")
        .param("amount", fee.amount())
        .param("currency", fee.currency().getCurrencyCode())
        .param("now", OffsetDateTime.now(clock))
        .update();
  }

  private static Extra extra(ResultSet row) throws SQLException {
    return new Extra(
        row.getString("code"),
        row.getString("name"),
        ExtraKind.valueOf(row.getString("kind")),
        Charge.valueOf(row.getString("charge")),
        money(row),
        row.getInt("max_quantity"),
        row.getBoolean("active"));
  }

  private static Money money(ResultSet row) throws SQLException {
    return new Money(row.getBigDecimal("amount"), Currency.getInstance(row.getString("currency")));
  }
}
