package psn.car.rental.administration.application;

/** The request names no country, or a country that does not exist. */
public class UnknownCountryException extends RuntimeException {

  public UnknownCountryException(String message) {
    super(message);
  }
}
