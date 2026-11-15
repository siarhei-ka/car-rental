package psn.car.rental.discount.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import psn.car.rental.discount.domain.AppliedDiscount;
import psn.car.rental.discount.domain.Basket;
import psn.car.rental.discount.domain.Conditions;
import psn.car.rental.discount.domain.DiscountRule;
import psn.car.rental.discount.domain.Discounts;
import psn.car.rental.discount.domain.Effect;
import psn.car.rental.discount.domain.InvalidDiscountException;
import psn.car.rental.discount.domain.Money;
import psn.car.rental.discount.domain.Target;
import psn.car.rental.discount.tenant.Countries;
import psn.car.rental.discount.tenant.Country;
import psn.car.rental.shared.events.Analytics;

/** Manages the discount rules of each country and chooses the discount of a basket. */
@Service
public class DiscountService {

  private final RuleStore store;
  private final Countries countries;
  private final Analytics analytics;

  public DiscountService(RuleStore store, Countries countries, Analytics analytics) {
    this.store = store;
    this.countries = countries;
    this.analytics = analytics;
  }

  public List<DiscountRule> rules(Country country) {
    return store.rules(country);
  }

  @Transactional
  public DiscountRule create(Country country, RuleChange change) {
    DiscountRule rule = toRule(country, UUID.randomUUID(), change);
    store.save(country, rule);
    record("admin.discount-rule-created", country, rule);
    return rule;
  }

  /**
   * Replaces a rule of the country.
   *
   * @throws RuleNotFoundException if the country has no rule with this ID
   */
  @Transactional
  public DiscountRule update(Country country, UUID id, RuleChange change) {
    store.find(country, id).orElseThrow(() -> new RuleNotFoundException(id));
    DiscountRule rule = toRule(country, id, change);
    store.save(country, rule);
    record("admin.discount-rule-changed", country, rule);
    return rule;
  }

  private void record(String type, Country country, DiscountRule rule) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("ruleId", rule.id().toString());
    data.put("name", rule.name());
    data.put("minExtras", rule.conditions().minExtras());
    data.put("requiredExtras", rule.conditions().requiredExtras().stream().sorted().toList());
    data.put("minDays", rule.conditions().minDays());
    switch (rule.effect()) {
      case Effect.PercentOff p -> data.put("percentOff", p.percent().toPlainString());
      case Effect.AmountOff a -> data.put("amountOff", a.amount().amount().toPlainString());
    }
    data.put("target", rule.effect().target().name());
    data.put("validFrom", rule.validFrom().toString());
    data.put("validTo", rule.validTo() == null ? null : rule.validTo().toString());
    data.put("active", rule.active());
    analytics.record(type, country.code(), data);
  }

  /**
   * The discount that saves the customer the most on the day, or empty if no rule applies.
   *
   * @param day the day the price is calculated, in the time zone of the pick-up location
   * @throws InvalidDiscountException if the basket is not in the currency of the country
   */
  public Optional<AppliedDiscount> evaluate(Country country, Basket basket, LocalDate day) {
    String currency = countries.currencyOf(country);
    if (!basket.total().currency().getCurrencyCode().equals(currency)) {
      throw new InvalidDiscountException("The basket is not in " + currency);
    }
    return Discounts.best(store.rules(country), basket, day);
  }

  private DiscountRule toRule(Country country, UUID id, RuleChange change) {
    Effect effect =
        switch (change.effect()) {
          case PERCENT_OFF -> new Effect.PercentOff(decimal(change.value()), change.target());
          case AMOUNT_OFF ->
              new Effect.AmountOff(
                  Money.of(change.value(), countries.currencyOf(country)), change.target());
        };
    return new DiscountRule(
        id,
        change.name(),
        new Conditions(change.minExtras(), change.requiredExtras(), change.minDays()),
        effect,
        change.validFrom(),
        change.validTo(),
        change.active());
  }

  private static BigDecimal decimal(String value) {
    try {
      return new BigDecimal(value);
    } catch (NumberFormatException | NullPointerException e) {
      throw new InvalidDiscountException("A percentage is a decimal number, such as 10 or 12.5");
    }
  }

  /** The kind of effect a rule has. */
  public enum EffectKind {
    PERCENT_OFF,
    AMOUNT_OFF
  }

  /**
   * The new state of a rule.
   *
   * @param value the percentage, or the amount in the currency of the country, as a decimal string
   */
  public record RuleChange(
      String name,
      Integer minExtras,
      Set<String> requiredExtras,
      Integer minDays,
      EffectKind effect,
      String value,
      Target target,
      LocalDate validFrom,
      LocalDate validTo,
      boolean active) {}
}
