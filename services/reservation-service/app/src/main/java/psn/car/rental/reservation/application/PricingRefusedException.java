package psn.car.rental.reservation.application;

/**
 * The pricing service refused the rental, for example because the driver is too young or the car is
 * not offered. Its answer is passed on to the customer unchanged.
 *
 * @see Pricing
 */
public class PricingRefusedException extends RuntimeException {

  private final int status;
  private final String type;
  private final String title;

  public PricingRefusedException(int status, String type, String title, String detail) {
    super(detail);
    this.status = status;
    this.type = type;
    this.title = title;
  }

  public int status() {
    return status;
  }

  /** The problem type of the pricing service, such as {@code driver-too-young}. */
  public String type() {
    return type;
  }

  public String title() {
    return title;
  }
}
