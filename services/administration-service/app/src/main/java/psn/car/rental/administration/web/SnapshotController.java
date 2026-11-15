package psn.car.rental.administration.web;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.administration.application.ReferenceDataService;
import psn.car.rental.administration.domain.CarClass;
import psn.car.rental.administration.domain.CarType;
import psn.car.rental.administration.domain.Country;
import psn.car.rental.administration.web.Bodies.LocationResponse;

/** All reference data at once, for services that load their copy at startup. */
@RestController
class SnapshotController {

  private final ReferenceDataService referenceData;

  SnapshotController(ReferenceDataService referenceData) {
    this.referenceData = referenceData;
  }

  @GetMapping("/api/v1/reference-data")
  SnapshotResponse snapshot() {
    ReferenceDataService.Snapshot snapshot = referenceData.snapshot();
    return new SnapshotResponse(
        snapshot.countries(),
        snapshot.carTypes(),
        snapshot.carClasses(),
        snapshot.locations().stream().map(LocationResponse::of).toList());
  }

  record SnapshotResponse(
      List<Country> countries,
      List<CarType> carTypes,
      List<CarClass> carClasses,
      List<LocationResponse> locations) {}
}
