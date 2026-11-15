package psn.car.rental.inventory.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * Cars sent by an administrator to another location of the same country.
 *
 * <p>The cars leave the source stock at the departure time. They join the target stock only when
 * their arrival is registered. The planned arrival is information only.
 */
public record Transfer(
    TransferId id, int cars, Instant departure, LocationId target, Instant plannedArrival) {

  public Transfer {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(departure, "departure");
    Objects.requireNonNull(target, "target");
    Objects.requireNonNull(plannedArrival, "plannedArrival");
    if (cars < 1) {
      throw new IllegalArgumentException("A transfer moves at least 1 car, but was " + cars);
    }
    if (!plannedArrival.isAfter(departure)) {
      throw new IllegalArgumentException("The planned arrival must be after the departure");
    }
  }

  boolean isSameAs(int otherCars, Instant otherDeparture, LocationId otherTarget) {
    return cars == otherCars && departure.equals(otherDeparture) && target.equals(otherTarget);
  }
}
