package psn.car.rental.discount.domain;

/** A rule that applies to a basket, with the amount it saves. */
public record AppliedDiscount(DiscountRule rule, Money amount) {}
