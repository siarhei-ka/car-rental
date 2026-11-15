package psn.car.rental.search.domain;

/**
 * A list price as the pricing service gives it: a decimal amount as a string and its currency. The
 * search only passes it on, so it never turns into a floating-point number.
 */
public record Price(String amount, String currency) {}
