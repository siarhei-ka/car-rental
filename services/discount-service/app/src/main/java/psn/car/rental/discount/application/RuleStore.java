package psn.car.rental.discount.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import psn.car.rental.discount.domain.DiscountRule;
import psn.car.rental.discount.tenant.Country;

/** Where the discount rules of each country are kept. */
public interface RuleStore {

  /** The rules of a country ordered by name, also those switched off. */
  List<DiscountRule> rules(Country country);

  Optional<DiscountRule> find(Country country, UUID id);

  /** Creates the rule, or replaces the rule with the same ID. */
  void save(Country country, DiscountRule rule);
}
