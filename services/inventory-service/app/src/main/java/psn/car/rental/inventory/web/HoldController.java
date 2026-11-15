package psn.car.rental.inventory.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.inventory.application.InventoryService;
import psn.car.rental.inventory.application.PlacedHold;
import psn.car.rental.inventory.domain.HoldId;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.RentalPeriod;
import psn.car.rental.inventory.tenant.Countries;
import psn.car.rental.inventory.web.Dtos.HoldRequest;
import psn.car.rental.inventory.web.Dtos.HoldResponse;

/** Holds, used by the reservation service. */
@RestController
@RequestMapping("/api/v1/holds")
class HoldController {

  private final InventoryService inventory;
  private final Countries countries;

  HoldController(InventoryService inventory, Countries countries) {
    this.inventory = inventory;
    this.countries = countries;
  }

  /** Keeps one car. The hold ID is the reservation ID, so a repeated request is harmless. */
  @PutMapping("/{holdId}")
  ResponseEntity<HoldResponse> place(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String holdId,
      @Valid @RequestBody HoldRequest request) {
    PlacedHold placed =
        inventory.placeHold(
            countries.require(country),
            Dtos.key(request.locationId(), request.carType(), request.carClass()),
            new HoldId(holdId),
            new RentalPeriod(request.start(), request.days()),
            new LocationId(request.returnLocationId()));
    return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(HoldResponse.of(placed.key(), placed.hold()));
  }

  @PostMapping("/{holdId}/confirm")
  HoldResponse confirm(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String holdId) {
    PlacedHold confirmed = inventory.confirmHold(countries.require(country), new HoldId(holdId));
    return HoldResponse.of(confirmed.key(), confirmed.hold());
  }

  /** Releases the car after a cancellation or a no-show. Releasing twice is harmless. */
  @DeleteMapping("/{holdId}")
  ResponseEntity<Void> release(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String holdId) {
    inventory.releaseHold(countries.require(country), new HoldId(holdId));
    return ResponseEntity.noContent().build();
  }

  /** Registers that the car was returned. */
  @PostMapping("/{holdId}/return")
  ResponseEntity<Void> registerReturn(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String holdId) {
    inventory.registerReturn(countries.require(country), new HoldId(holdId));
    return ResponseEntity.noContent().build();
  }
}
