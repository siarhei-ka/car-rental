package psn.car.rental.search.domain;

/** The query breaks a rule, or names a location that cannot be used. */
public class InvalidSearchException extends RuntimeException {

  public InvalidSearchException(String message) {
    super(message);
  }
}
