package psn.car.rental.administration.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.administration.application.ReferenceDataService;
import psn.car.rental.administration.application.Saved;
import psn.car.rental.administration.application.UnknownCountryException;
import psn.car.rental.administration.domain.CarClass;
import psn.car.rental.administration.domain.CarType;
import psn.car.rental.administration.domain.Country;
import psn.car.rental.administration.domain.Location;
import psn.car.rental.administration.web.Bodies.CountryRequest;
import psn.car.rental.administration.web.Bodies.LocationRequest;
import psn.car.rental.administration.web.Bodies.LocationResponse;
import psn.car.rental.administration.web.Bodies.NamedRequest;

/**
 * Administration of reference data. Countries, car types and car classes are shared and will be
 * restricted to the super administrator, and locations to administrators of their country, when the
 * API gateway arrives (build step 4).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

  private final ReferenceDataService referenceData;

  AdminController(ReferenceDataService referenceData) {
    this.referenceData = referenceData;
  }

  @GetMapping("/countries")
  List<Country> countries() {
    return referenceData.countries();
  }

  @PutMapping("/countries/{code}")
  ResponseEntity<Country> saveCountry(
      @PathVariable @Pattern(regexp = Bodies.COUNTRY) String code,
      @Valid @RequestBody CountryRequest request) {
    return respond(
        referenceData.saveCountry(
            new Country(code, request.name(), request.currency(), request.active())));
  }

  @GetMapping("/car-types")
  List<CarType> carTypes() {
    return referenceData.carTypes();
  }

  @PutMapping("/car-types/{code}")
  ResponseEntity<CarType> saveCarType(
      @PathVariable @Pattern(regexp = Bodies.CODE) String code,
      @Valid @RequestBody NamedRequest request) {
    return respond(referenceData.saveCarType(new CarType(code, request.name(), request.active())));
  }

  @GetMapping("/car-classes")
  List<CarClass> carClasses() {
    return referenceData.carClasses();
  }

  @PutMapping("/car-classes/{code}")
  ResponseEntity<CarClass> saveCarClass(
      @PathVariable @Pattern(regexp = Bodies.CODE) String code,
      @Valid @RequestBody NamedRequest request) {
    return respond(
        referenceData.saveCarClass(new CarClass(code, request.name(), request.active())));
  }

  @GetMapping("/locations")
  List<LocationResponse> locations(
      @RequestHeader(name = "X-Country", required = false) String country) {
    return referenceData.locations(requireCountry(country)).stream()
        .map(LocationResponse::of)
        .toList();
  }

  @GetMapping("/locations/{id}")
  LocationResponse location(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Bodies.ID) String id) {
    return LocationResponse.of(referenceData.location(requireCountry(country), id));
  }

  @PutMapping("/locations/{id}")
  ResponseEntity<LocationResponse> saveLocation(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Bodies.ID) String id,
      @Valid @RequestBody LocationRequest request) {
    Saved<Location> saved =
        referenceData.saveLocation(
            new Location(
                id,
                requireCountry(country),
                request.name(),
                request.city(),
                request.latitude(),
                request.longitude(),
                Location.timeZone(request.timeZone()),
                request.active()));
    return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(LocationResponse.of(saved.value()));
  }

  private static <T> ResponseEntity<T> respond(Saved<T> saved) {
    return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(saved.value());
  }

  private static String requireCountry(String country) {
    if (country == null || !country.matches(Bodies.COUNTRY)) {
      throw new UnknownCountryException("The X-Country header must name a country");
    }
    return country;
  }
}
