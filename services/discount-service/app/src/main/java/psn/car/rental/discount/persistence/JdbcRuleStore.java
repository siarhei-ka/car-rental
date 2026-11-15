package psn.car.rental.discount.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.discount.application.RuleStore;
import psn.car.rental.discount.domain.Conditions;
import psn.car.rental.discount.domain.DiscountRule;
import psn.car.rental.discount.domain.Effect;
import psn.car.rental.discount.domain.Effect.AmountOff;
import psn.car.rental.discount.domain.Effect.PercentOff;
import psn.car.rental.discount.domain.Money;
import psn.car.rental.discount.domain.Target;
import psn.car.rental.discount.tenant.Country;

/**
 * Discount rules in the schema of their country. The schema name comes from a validated country
 * code, so it is safe in SQL.
 */
@Repository
class JdbcRuleStore implements RuleStore {

  private static final String COLUMNS =
      "id, name, min_extras, required_extras, min_days, effect, percent, amount, currency, target,"
          + " valid_from, valid_to, active";

  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcRuleStore(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public List<DiscountRule> rules(Country country) {
    return jdbc.sql(
            "select " + COLUMNS + " from " + country.schema() + ".discount_rule order by name, id")
        .query((row, n) -> rule(row))
        .list();
  }

  @Override
  public Optional<DiscountRule> find(Country country, UUID id) {
    return jdbc.sql(
            "select " + COLUMNS + " from " + country.schema() + ".discount_rule where id = :id")
        .param("id", id)
        .query((row, n) -> rule(row))
        .optional();
  }

  @Override
  public void save(Country country, DiscountRule rule) {
    Effect effect = rule.effect();
    jdbc.sql(
            "insert into "
                + country.schema()
                + ".discount_rule ("
                + COLUMNS
                + ", updated_at) values (:id, :name, :minExtras, :requiredExtras, :minDays,"
                + " :effect, :percent, :amount, :currency, :target, :validFrom, :validTo,"
                + " :active, :now) on conflict (id) do update set name = excluded.name,"
                + " min_extras = excluded.min_extras, required_extras = excluded.required_extras,"
                + " min_days = excluded.min_days, effect = excluded.effect,"
                + " percent = excluded.percent, amount = excluded.amount,"
                + " currency = excluded.currency, target = excluded.target,"
                + " valid_from = excluded.valid_from, valid_to = excluded.valid_to,"
                + " active = excluded.active, updated_at = excluded.updated_at")
        .param("id", rule.id())
        .param("name", rule.name())
        .param("minExtras", rule.conditions().minExtras())
        .param(
            "requiredExtras",
            String.join(",", rule.conditions().requiredExtras().stream().sorted().toList()))
        .param("minDays", rule.conditions().minDays())
        .param("effect", effect instanceof PercentOff ? "PERCENT_OFF" : "AMOUNT_OFF")
        .param("percent", effect instanceof PercentOff p ? p.percent() : null)
        .param("amount", effect instanceof AmountOff a ? a.amount().amount() : null)
        .param(
            "currency",
            effect instanceof AmountOff a ? a.amount().currency().getCurrencyCode() : null)
        .param("target", effect.target().name())
        .param("validFrom", rule.validFrom())
        .param("validTo", rule.validTo())
        .param("active", rule.active())
        .param("now", OffsetDateTime.now(clock))
        .update();
  }

  private static DiscountRule rule(ResultSet row) throws SQLException {
    Target target = Target.valueOf(row.getString("target"));
    Effect effect =
        "PERCENT_OFF".equals(row.getString("effect"))
            ? new PercentOff(row.getBigDecimal("percent"), target)
            : new AmountOff(
                new Money(
                    row.getBigDecimal("amount"), Currency.getInstance(row.getString("currency"))),
                target);
    return new DiscountRule(
        row.getObject("id", UUID.class),
        row.getString("name"),
        new Conditions(
            row.getObject("min_extras", Integer.class),
            codes(row.getString("required_extras")),
            row.getObject("min_days", Integer.class)),
        effect,
        row.getObject("valid_from", LocalDate.class),
        row.getObject("valid_to", LocalDate.class),
        row.getBoolean("active"));
  }

  private static Set<String> codes(String joined) {
    return joined.isEmpty()
        ? Set.of()
        : Arrays.stream(joined.split(",")).collect(Collectors.toUnmodifiableSet());
  }
}
