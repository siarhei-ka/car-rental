package psn.car.rental.pricing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.pricing.application.RateService;
import psn.car.rental.pricing.domain.RateKey;
import psn.car.rental.pricing.tenant.Countries;

/**
 * Daily rates. The list prices of a location are public; administrators of the country change them.
 */
@RestController
class RateController {

  static final String ID = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
  static final String CODE = "[A-Z][A-Z0-9_]{0,31}";

  private final RateService rates;
  private final Countries countries;

  RateController(RateService rates, Countries countries) {
    this.rates = rates;
    this.countries = countries;
  }

  /** The list prices of a location, for every car type and class that has a rate. */
  @GetMapping("/api/v1/rates")
  List<RateResponse> listPrices(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam @Pattern(regexp = ID) String locationId) {
    return rates.list(countries.require(country), Optional.of(locationId)).stream()
        .map(RateResponse::of)
        .toList();
  }

  @GetMapping("/api/v1/admin/rates")
  List<RateResponse> listRates(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam(required = false) @Pattern(regexp = ID) String locationId) {
    return rates.list(countries.require(country), Optional.ofNullable(locationId)).stream()
        .map(RateResponse::of)
        .toList();
  }

  @PutMapping("/api/v1/admin/rates/{locationId}/{carType}/{carClass}")
  RateResponse saveRate(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = ID) String locationId,
      @PathVariable @Pattern(regexp = CODE) String carType,
      @PathVariable @Pattern(regexp = CODE) String carClass,
      @Valid @RequestBody RateRequest request) {
    return RateResponse.of(
        rates.save(
            countries.require(country),
            new RateKey(locationId, carType, carClass),
            request.amount()));
  }

  /** The amount is a decimal string in the currency of the country, such as "149.00". */
  record RateRequest(@NotNull String amount) {}
}
