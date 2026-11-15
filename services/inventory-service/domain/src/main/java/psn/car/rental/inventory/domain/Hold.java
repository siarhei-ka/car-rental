package psn.car.rental.inventory.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * One car kept for a reservation.
 *
 * @param oneWay true when the car is returned to a location other than the pick-up location
 */
public record Hold(
    HoldId id,
    RentalPeriod period,
    LocationId returnLocation,
    boolean oneWay,
    HoldStatus status,
    Instant createdAt) {

  public Hold {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(period, "period");
    Objects.requireNonNull(returnLocation, "returnLocation");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * The moment the car is free again at the pick-up location. A one-way hold never gives the car
   * back to the pick-up location, so it has no such moment.
   */
  public Optional<Instant> usedUntil() {
    return oneWay ? Optional.empty() : Optional.of(period.end());
  }

  Hold confirmed() {
    return new Hold(id, period, returnLocation, oneWay, HoldStatus.CONFIRMED, createdAt);
  }

  boolean isFor(RentalPeriod otherPeriod, LocationId otherReturnLocation) {
    return period.equals(otherPeriod) && returnLocation.equals(otherReturnLocation);
  }
}
