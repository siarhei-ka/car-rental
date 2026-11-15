package psn.car.rental.customer.application;

/** The customer has not entered a driver profile yet. */
public class ProfileNotFoundException extends RuntimeException {

  public ProfileNotFoundException() {
    super("No driver profile has been entered yet");
  }
}
