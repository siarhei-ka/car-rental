package psn.car.rental.pricing.domain;

/** A valid request that cannot be priced, because the rental is not offered as asked. */
public class QuoteRefusedException extends RuntimeException {

  /** Why the rental cannot be priced. */
  public enum Reason {
    /** The driver is younger than the minimum age on the start of the rental. */
    DRIVER_TOO_YOUNG,
    /** The car type and class have no daily rate at the pick-up location. */
    CAR_NOT_OFFERED,
    /** The country has no one-way fee, so a rental to another location cannot be priced. */
    ONE_WAY_NOT_OFFERED,
    /** A location is switched off. */
    LOCATION_NOT_OFFERED
  }

  private final Reason reason;

  public QuoteRefusedException(Reason reason, String message) {
    super(message);
    this.reason = reason;
  }

  public Reason reason() {
    return reason;
  }
}
