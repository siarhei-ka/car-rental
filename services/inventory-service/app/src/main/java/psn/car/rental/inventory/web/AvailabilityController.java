package psn.car.rental.inventory.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.inventory.application.InventoryService;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.RentalPeriod;
import psn.car.rental.inventory.tenant.Countries;
import psn.car.rental.inventory.web.Dtos.AvailabilityResponse;

/** Availability for a rental, used by the search service. */
@RestController
class AvailabilityController {

  private final InventoryService inventory;
  private final Countries countries;

  AvailabilityController(InventoryService inventory, Countries countries) {
    this.inventory = inventory;
    this.countries = countries;
  }

  @GetMapping("/api/v1/availability")
  List<AvailabilityResponse> availability(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam @Pattern(regexp = Dtos.ID) String locationId,
      @RequestParam Instant start,
      @RequestParam @Min(1) @Max(366) int days,
      @RequestParam(required = false) @Pattern(regexp = Dtos.ID) String returnLocationId) {
    LocationId pickUp = new LocationId(locationId);
    LocationId returnTo = returnLocationId == null ? pickUp : new LocationId(returnLocationId);
    return inventory
        .availability(countries.require(country), pickUp, new RentalPeriod(start, days), returnTo)
        .stream()
        .map(AvailabilityResponse::of)
        .toList();
  }
}
