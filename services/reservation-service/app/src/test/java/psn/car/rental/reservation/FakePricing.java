package psn.car.rental.reservation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import psn.car.rental.reservation.application.Pricing;
import psn.car.rental.reservation.application.PricingRefusedException;
import psn.car.rental.reservation.application.PricingUnavailableException;
import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.tenant.Country;

/**
 * Stands in for the pricing service: every day costs {@value #PER_DAY} PLN, unless a test sets
 * another price, a refusal or a failure. The calculation itself is tested in the pricing service.
 */
public class FakePricing implements Pricing {

  static final String PER_DAY = "100.00";

  private final List<String> calls = new CopyOnWriteArrayList<>();
  private volatile BigDecimal perDay = new BigDecimal(PER_DAY);
  private volatile RuntimeException failure;

  public void reset() {
    calls.clear();
    perDay = new BigDecimal(PER_DAY);
    failure = null;
  }

  /** From now on, a day costs this much. */
  public void perDay(String amount) {
    perDay = new BigDecimal(amount);
  }

  public void refuse(int status, String type, String detail) {
    failure = new PricingRefusedException(status, type, "The rental cannot be priced", detail);
  }

  public void fail() {
    failure = new PricingUnavailableException("The pricing service is not available", null);
  }

  /** Each call as country:customer:insurance:extras. */
  public List<String> calls() {
    return List.copyOf(calls);
  }

  /** The total of a rental of some days at the standard price, as the UI would have seen it. */
  public static String total(int days) {
    return new BigDecimal(PER_DAY).multiply(BigDecimal.valueOf(days)).toPlainString();
  }

  @Override
  public Price quote(Country country, String customerId, RentalRequest rental) {
    calls.add(
        country.code()
            + ":"
            + customerId
            + ":"
            + rental.insurance()
            + ":"
            + rental.extras().size());
    if (failure != null) {
      throw failure;
    }
    BigDecimal total = perDay.multiply(BigDecimal.valueOf(rental.days()));
    return new Price(
        "PLN",
        List.of(
            new Price.Line(
                "CAR_RENTAL",
                rental.carType() + "/" + rental.carClass(),
                "Car rental",
                1,
                perDay,
                "PER_DAY",
                total)),
        total,
        Optional.empty(),
        total);
  }
}
