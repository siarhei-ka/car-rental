package psn.car.rental.reservation.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import psn.car.rental.reservation.domain.ExtraChoice;
import psn.car.rental.reservation.domain.RentalRequest;

/**
 * The body of a create request. It matches {@code openapi.yaml}.
 *
 * @param expectedTotal the total the customer saw on the price page. It is only compared with the
 *     price calculated now; it is never used as the price
 */
record ReservationRequest(
    @NotBlank @Pattern(regexp = Patterns.ID) String pickUpLocationId,
    @NotBlank @Pattern(regexp = Patterns.ID) String returnLocationId,
    @NotBlank @Pattern(regexp = Patterns.CODE) String carType,
    @NotBlank @Pattern(regexp = Patterns.CODE) String carClass,
    @NotNull Instant start,
    @NotNull @Min(1) @Max(366) Integer days,
    @NotBlank @Pattern(regexp = Patterns.CODE) String insurance,
    @Valid @Size(max = 20) List<Extra> extras,
    @Valid @NotNull Total expectedTotal) {

  RentalRequest toRental() {
    return new RentalRequest(
        pickUpLocationId,
        returnLocationId,
        carType,
        carClass,
        start,
        days,
        insurance,
        extras == null
            ? List.of()
            : extras.stream().map(e -> new ExtraChoice(e.code(), e.quantity())).toList());
  }

  record Extra(
      @NotBlank @Pattern(regexp = Patterns.CODE) String code, @NotNull @Min(1) Integer quantity) {}

  /** An amount as a decimal string with its currency. */
  record Total(
      @NotNull @Pattern(regexp = "-?\\d{1,12}(\\.\\d{1,3})?") String amount,
      @NotNull @Pattern(regexp = "[A-Z]{3}") String currency) {

    BigDecimal value() {
      return new BigDecimal(amount);
    }
  }
}
