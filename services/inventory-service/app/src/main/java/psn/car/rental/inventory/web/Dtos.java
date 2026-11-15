package psn.car.rental.inventory.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import psn.car.rental.inventory.application.AvailableCars;
import psn.car.rental.inventory.application.LedgerStore;
import psn.car.rental.inventory.domain.CarClassCode;
import psn.car.rental.inventory.domain.CarTypeCode;
import psn.car.rental.inventory.domain.Hold;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.domain.Transfer;

/** Request and response bodies of the inventory API. They match {@code openapi.yaml}. */
final class Dtos {

  static final String ID = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
  static final String CODE = "[A-Z][A-Z0-9_]{0,31}";

  private Dtos() {}

  static StockKey key(String location, String carType, String carClass) {
    return new StockKey(
        new LocationId(location), new CarTypeCode(carType), new CarClassCode(carClass));
  }

  record HoldRequest(
      @NotBlank @Pattern(regexp = ID) String locationId,
      @NotBlank @Pattern(regexp = CODE) String carType,
      @NotBlank @Pattern(regexp = CODE) String carClass,
      @NotNull Instant start,
      @NotNull @Min(1) @Max(366) Integer days,
      @NotBlank @Pattern(regexp = ID) String returnLocationId) {}

  record HoldResponse(
      String id,
      String locationId,
      String carType,
      String carClass,
      Instant start,
      int days,
      Instant end,
      String returnLocationId,
      boolean oneWay,
      String status) {

    static HoldResponse of(StockKey key, Hold hold) {
      return new HoldResponse(
          hold.id().value(),
          key.location().value(),
          key.carType().value(),
          key.carClass().value(),
          hold.period().start(),
          hold.period().days(),
          hold.period().end(),
          hold.returnLocation().value(),
          hold.oneWay(),
          hold.status().name());
    }
  }

  record AvailabilityResponse(String carType, String carClass, int availableCars) {

    static AvailabilityResponse of(AvailableCars available) {
      return new AvailabilityResponse(
          available.key().carType().value(), available.key().carClass().value(), available.cars());
    }
  }

  record StockRequest(@NotNull @Min(0) @Max(100_000) Integer cars) {}

  record StockResponse(String locationId, String carType, String carClass, int cars) {

    static StockResponse of(LedgerStore.StockRow row) {
      return new StockResponse(
          row.key().location().value(),
          row.key().carType().value(),
          row.key().carClass().value(),
          row.cars());
    }
  }

  record TransferRequest(
      @NotBlank @Pattern(regexp = ID) String locationId,
      @NotBlank @Pattern(regexp = CODE) String carType,
      @NotBlank @Pattern(regexp = CODE) String carClass,
      @NotNull @Min(1) @Max(1_000) Integer cars,
      @NotNull Instant departure,
      @NotBlank @Pattern(regexp = ID) String targetLocationId,
      @NotNull Instant plannedArrival) {}

  record TransferResponse(
      String id,
      String locationId,
      String carType,
      String carClass,
      int cars,
      Instant departure,
      String targetLocationId,
      Instant plannedArrival) {

    static TransferResponse of(StockKey key, Transfer transfer) {
      return new TransferResponse(
          transfer.id().value(),
          key.location().value(),
          key.carType().value(),
          key.carClass().value(),
          transfer.cars(),
          transfer.departure(),
          transfer.target().value(),
          transfer.plannedArrival());
    }
  }
}
