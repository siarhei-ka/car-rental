package psn.car.rental.discount.domain;

import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A discount rule of a country, for example "3 or more extras: 10% off the extras". Rules are data
 * that an administrator adds. They are never deleted, only switched off.
 *
 * @param validFrom the first day the rule applies
 * @param validTo the last day the rule applies, or null for no end
 */
public record DiscountRule(
    UUID id,
    String name,
    Conditions conditions,
    Effect effect,
    LocalDate validFrom,
    LocalDate validTo,
    boolean active) {

  public DiscountRule {
    Objects.requireNonNull(id, "id");
    if (name == null || name.isBlank() || name.length() > 100) {
      throw new InvalidDiscountException("The name of a rule has 1 to 100 characters");
    }
    name = name.strip();
    Objects.requireNonNull(conditions, "conditions");
    Objects.requireNonNull(effect, "effect");
    if (validFrom == null) {
      throw new InvalidDiscountException("A rule has a first day");
    }
    if (validTo != null && validTo.isBefore(validFrom)) {
      throw new InvalidDiscountException("The last day of a rule is not before its first day");
    }
  }

  /** Whether the rule is switched on and the day is within its validity period. */
  public boolean appliesOn(LocalDate day) {
    return active && !day.isBefore(validFrom) && (validTo == null || !day.isAfter(validTo));
  }

  /**
   * The saving this rule gives the basket on a day, or empty if it does not apply or saves nothing.
   */
  public Optional<AppliedDiscount> applyTo(Basket basket, LocalDate day) {
    if (!appliesOn(day) || !conditions.holdFor(basket)) {
      return Optional.empty();
    }
    Money saving = effect.savingOn(basket.base(effect.target()));
    return saving.isZero() ? Optional.empty() : Optional.of(new AppliedDiscount(this, saving));
  }
}
