package psn.car.rental.discount.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.discount.application.DiscountService;
import psn.car.rental.discount.application.DiscountService.EffectKind;
import psn.car.rental.discount.application.DiscountService.RuleChange;
import psn.car.rental.discount.domain.AppliedDiscount;
import psn.car.rental.discount.domain.Basket;
import psn.car.rental.discount.domain.DiscountRule;
import psn.car.rental.discount.domain.Effect;
import psn.car.rental.discount.domain.Money;
import psn.car.rental.discount.domain.Target;
import psn.car.rental.discount.tenant.Countries;

/**
 * Discount rules, which administrators of the country manage, and the discount of a basket, which
 * the pricing service asks for.
 */
@RestController
class DiscountController {

  private final DiscountService discounts;
  private final Countries countries;

  DiscountController(DiscountService discounts, Countries countries) {
    this.discounts = discounts;
    this.countries = countries;
  }

  /** All rules of the country, also those switched off, ordered by name. */
  @GetMapping("/api/v1/admin/discount-rules")
  List<RuleResponse> rules(@RequestHeader(name = "X-Country", required = false) String country) {
    return discounts.rules(countries.require(country)).stream().map(RuleResponse::of).toList();
  }

  @PostMapping("/api/v1/admin/discount-rules")
  @ResponseStatus(HttpStatus.CREATED)
  RuleResponse create(
      @RequestHeader(name = "X-Country", required = false) String country,
      @Valid @RequestBody RuleRequest request) {
    return RuleResponse.of(discounts.create(countries.require(country), request.toChange()));
  }

  @PutMapping("/api/v1/admin/discount-rules/{id}")
  RuleResponse update(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable UUID id,
      @Valid @RequestBody RuleRequest request) {
    return RuleResponse.of(discounts.update(countries.require(country), id, request.toChange()));
  }

  /** The discount that saves the customer the most, or none. Only for other services. */
  @PostMapping("/api/v1/discount-evaluations")
  EvaluationResponse evaluate(
      @RequestHeader(name = "X-Country", required = false) String country,
      @Valid @RequestBody EvaluationRequest request) {
    Basket basket =
        new Basket(
            request.days(),
            request.total().toMoney(),
            request.extras() == null
                ? List.of()
                : request.extras().stream()
                    .map(e -> new Basket.Line(e.code(), e.quantity(), e.price().toMoney()))
                    .toList());
    return new EvaluationResponse(
        discounts
            .evaluate(countries.require(country), basket, request.date())
            .map(DiscountResponse::of)
            .orElse(null));
  }

  record RuleRequest(
      @NotNull String name,
      @Valid @NotNull ConditionsJson conditions,
      @Valid @NotNull EffectRequest effect,
      @NotNull LocalDate validFrom,
      LocalDate validTo,
      boolean active) {

    RuleChange toChange() {
      return new RuleChange(
          name,
          conditions.minExtras(),
          conditions.requiredExtras(),
          conditions.minDays(),
          effect.kind(),
          effect.kind() == EffectKind.PERCENT_OFF ? effect.percent() : effect.amount(),
          effect.target(),
          validFrom,
          validTo,
          active);
    }
  }

  record ConditionsJson(Integer minExtras, Set<String> requiredExtras, Integer minDays) {}

  /**
   * @param percent the percentage off, for a rule of kind PERCENT_OFF
   * @param amount the amount off in the currency of the country, for a rule of kind AMOUNT_OFF
   */
  record EffectRequest(
      @NotNull EffectKind kind, String percent, String amount, @NotNull Target target) {}

  record RuleResponse(
      UUID id,
      String name,
      ConditionsJson conditions,
      EffectResponse effect,
      LocalDate validFrom,
      LocalDate validTo,
      boolean active) {

    static RuleResponse of(DiscountRule rule) {
      return new RuleResponse(
          rule.id(),
          rule.name(),
          new ConditionsJson(
              rule.conditions().minExtras(),
              Set.copyOf(rule.conditions().requiredExtras()),
              rule.conditions().minDays()),
          EffectResponse.of(rule.effect()),
          rule.validFrom(),
          rule.validTo(),
          rule.active());
    }
  }

  record EffectResponse(EffectKind kind, String percent, MoneyJson amount, Target target) {

    static EffectResponse of(Effect effect) {
      return switch (effect) {
        case Effect.PercentOff p ->
            new EffectResponse(
                EffectKind.PERCENT_OFF, p.percent().toPlainString(), null, p.target());
        case Effect.AmountOff a ->
            new EffectResponse(EffectKind.AMOUNT_OFF, null, MoneyJson.of(a.amount()), a.target());
      };
    }
  }

  record EvaluationRequest(
      @NotNull LocalDate date,
      @Min(1) @Max(365) int days,
      @Valid @NotNull MoneyJson total,
      @Valid List<ExtraLine> extras) {}

  record ExtraLine(@NotNull String code, int quantity, @Valid @NotNull MoneyJson price) {}

  /** The discount of a basket, or null when no rule applies. */
  record EvaluationResponse(DiscountResponse discount) {}

  record DiscountResponse(UUID ruleId, String name, Target target, MoneyJson amount) {

    static DiscountResponse of(AppliedDiscount applied) {
      return new DiscountResponse(
          applied.rule().id(),
          applied.rule().name(),
          applied.rule().effect().target(),
          MoneyJson.of(applied.amount()));
    }
  }

  /** An amount as a decimal string, so that no client turns it into a floating-point number. */
  record MoneyJson(@NotNull String amount, @NotNull String currency) {

    static MoneyJson of(Money money) {
      return new MoneyJson(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }

    Money toMoney() {
      return Money.of(amount, currency);
    }
  }
}
