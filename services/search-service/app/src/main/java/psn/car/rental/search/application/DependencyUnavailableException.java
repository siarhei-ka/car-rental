package psn.car.rental.search.application;

/** Another service did not answer in time or failed, so the search cannot be answered. */
public class DependencyUnavailableException extends RuntimeException {

  public DependencyUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
