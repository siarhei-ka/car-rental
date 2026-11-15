package psn.car.rental.ancillary.application;

/** The country has no one-way fee yet. */
public class OneWayFeeNotSetException extends RuntimeException {

  public OneWayFeeNotSetException() {
    super("No one-way fee is set for this country");
  }
}
