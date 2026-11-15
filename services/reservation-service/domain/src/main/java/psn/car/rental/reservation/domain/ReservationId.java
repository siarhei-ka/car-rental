package psn.car.rental.reservation.domain;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Identifies a reservation. The inventory service uses it as the ID of the reservation's hold. */
public record ReservationId(UUID value) {

  public ReservationId {
    Objects.requireNonNull(value, "value");
  }

  /**
   * The ID that belongs to an idempotency key of a customer. The same key always gives the same ID,
   * so two concurrent repeats of a request place the same hold and only one reservation is stored.
   * Two customers who happen to send the same key get different reservations.
   */
  public static ReservationId forIdempotencyKey(
      String country, String customerId, String idempotencyKey) {
    String name =
        "reservation:"
            + country
            + ":"
            + customerId.length()
            + ":"
            + customerId
            + ":"
            + idempotencyKey;
    return new ReservationId(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)));
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
