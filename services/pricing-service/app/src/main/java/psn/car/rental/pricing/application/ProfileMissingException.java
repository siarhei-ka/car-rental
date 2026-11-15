package psn.car.rental.pricing.application;

/** The customer has not entered a driver profile, and the price depends on the driver. */
public class ProfileMissingException extends RuntimeException {

  public ProfileMissingException() {
    super("Enter your driver profile before asking for the price");
  }
}
