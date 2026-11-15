package psn.car.rental.customer.domain;

/** The ID of a customer: the ID of the user in Keycloak, taken from the access token. */
public record CustomerId(String value) {

  public CustomerId {
    if (value == null || value.isBlank() || value.length() > 255) {
      throw new InvalidProfileException("A customer ID has 1 to 255 characters");
    }
  }
}
