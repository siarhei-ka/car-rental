package psn.car.rental.discount.tenant;

/** The request names no country, or a country that this service does not serve. */
public class UnknownCountryException extends RuntimeException {

  public UnknownCountryException(String message) {
    super(message);
  }
}
