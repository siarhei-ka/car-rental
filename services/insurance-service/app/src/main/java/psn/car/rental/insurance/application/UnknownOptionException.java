package psn.car.rental.insurance.application;

/** The request names an insurance option that does not exist. */
public class UnknownOptionException extends RuntimeException {

  public UnknownOptionException(String code) {
    super("There is no insurance option " + code);
  }
}
