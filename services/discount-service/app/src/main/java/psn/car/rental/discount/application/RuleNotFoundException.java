package psn.car.rental.discount.application;

import java.util.UUID;

/** The country has no discount rule with this ID. */
public class RuleNotFoundException extends RuntimeException {

  public RuleNotFoundException(UUID id) {
    super("There is no discount rule " + id + " in this country");
  }
}
