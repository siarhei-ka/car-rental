package psn.car.rental.reservation.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.Reservation;

/**
 * A reservation as the API shows it.
 *
 * @param country the country of the reservation, which every later request about it names
 * @param customerId the customer who made the reservation
 * @param price the price the reservation was made with; null only for reservations made before
 *     prices were stored
 * @param inventoryUpToDate false while a change still has to reach the inventory service
 */
record ReservationResponse(
    String id,
    String country,
    String customerId,
    String pickUpLocationId,
    String returnLocationId,
    String carType,
    String carClass,
    Instant start,
    int days,
    Instant end,
    boolean oneWay,
    String insurance,
    List<ExtraResponse> extras,
    PriceResponse price,
    String state,
    String paymentMethod,
    boolean inventoryUpToDate) {

  static ReservationResponse of(String country, Reservation reservation) {
    RentalRequest rental = reservation.rental();
    return new ReservationResponse(
        reservation.id().toString(),
        country,
        reservation.customerId(),
        rental.pickUpLocationId(),
        rental.returnLocationId(),
        rental.carType(),
        rental.carClass(),
        rental.start(),
        rental.days(),
        rental.end(),
        rental.oneWay(),
        rental.insurance(),
        rental.extras().stream().map(e -> new ExtraResponse(e.code(), e.quantity())).toList(),
        reservation.price().map(PriceResponse::of).orElse(null),
        reservation.state().name(),
        "PAY_AT_PICK_UP",
        reservation.pendingAction().isEmpty());
  }

  record ExtraResponse(String code, int quantity) {}

  /** An amount as a decimal string, so that no client turns it into a floating-point number. */
  record MoneyResponse(String amount, String currency) {

    static MoneyResponse of(BigDecimal amount, String currency) {
      int digits = java.util.Currency.getInstance(currency).getDefaultFractionDigits();
      return new MoneyResponse(
          amount.setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString(), currency);
    }
  }

  record LineResponse(
      String kind,
      String code,
      String name,
      int quantity,
      MoneyResponse unitPrice,
      String charge,
      MoneyResponse amount) {}

  record DiscountResponse(String name, MoneyResponse amount) {}

  /** The price, in the same form as a quote of the pricing service. */
  record PriceResponse(
      String currency,
      List<LineResponse> lines,
      MoneyResponse subtotal,
      DiscountResponse discount,
      MoneyResponse total) {

    static PriceResponse of(Price price) {
      String c = price.currency();
      return new PriceResponse(
          c,
          price.lines().stream()
              .map(l ->
                  new LineResponse(
                      l.kind(),
                      l.code(),
                      l.name(),
                      l.quantity(),
                      MoneyResponse.of(l.unitPrice(), c),
                      l.charge(),
                      MoneyResponse.of(l.amount(), c)))
              .toList(),
          MoneyResponse.of(price.subtotal(), c),
          price
              .discount()
              .map(d -> new DiscountResponse(d.name(), MoneyResponse.of(d.amount(), c)))
              .orElse(null),
          MoneyResponse.of(price.total(), c));
    }
  }
}
