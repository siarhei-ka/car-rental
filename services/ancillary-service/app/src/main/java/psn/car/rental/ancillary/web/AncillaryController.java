package psn.car.rental.ancillary.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.ancillary.application.AncillaryService;
import psn.car.rental.ancillary.application.AncillaryService.ExtraChange;
import psn.car.rental.ancillary.domain.Charge;
import psn.car.rental.ancillary.domain.Extra;
import psn.car.rental.ancillary.domain.ExtraKind;
import psn.car.rental.ancillary.domain.Money;
import psn.car.rental.ancillary.tenant.Countries;

/**
 * Extras and the one-way fee. The extras on offer and the fee are public list prices;
 * administrators of the country change them.
 */
@RestController
class AncillaryController {

  static final String CODE = "[A-Z][A-Z0-9_]{0,31}";

  private final AncillaryService ancillaries;
  private final Countries countries;

  AncillaryController(AncillaryService ancillaries, Countries countries) {
    this.ancillaries = ancillaries;
    this.countries = countries;
  }

  /** The extras on offer in the country, ordered by name. */
  @GetMapping("/api/v1/extras")
  List<ExtraResponse> extras(@RequestHeader(name = "X-Country", required = false) String country) {
    return ancillaries.extras(countries.require(country), true).stream()
        .map(ExtraResponse::of)
        .toList();
  }

  /** The fee for returning the car to another location of the country. */
  @GetMapping("/api/v1/one-way-fee")
  MoneyResponse oneWayFee(@RequestHeader(name = "X-Country", required = false) String country) {
    return MoneyResponse.of(ancillaries.oneWayFee(countries.require(country)));
  }

  /** All extras of the country, also those switched off. */
  @GetMapping("/api/v1/admin/extras")
  List<ExtraResponse> allExtras(
      @RequestHeader(name = "X-Country", required = false) String country) {
    return ancillaries.extras(countries.require(country), false).stream()
        .map(ExtraResponse::of)
        .toList();
  }

  @PutMapping("/api/v1/admin/extras/{code}")
  ExtraResponse saveExtra(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = CODE) String code,
      @Valid @RequestBody ExtraRequest request) {
    return ExtraResponse.of(
        ancillaries.saveExtra(
            countries.require(country),
            new ExtraChange(
                code,
                request.name(),
                request.kind(),
                request.charge(),
                request.price(),
                request.maxQuantity(),
                request.active())));
  }

  @GetMapping("/api/v1/admin/one-way-fee")
  MoneyResponse adminOneWayFee(
      @RequestHeader(name = "X-Country", required = false) String country) {
    return oneWayFee(country);
  }

  @PutMapping("/api/v1/admin/one-way-fee")
  MoneyResponse saveOneWayFee(
      @RequestHeader(name = "X-Country", required = false) String country,
      @Valid @RequestBody FeeRequest request) {
    return MoneyResponse.of(
        ancillaries.saveOneWayFee(countries.require(country), request.amount()));
  }

  record ExtraRequest(
      @NotNull String name,
      @NotNull ExtraKind kind,
      @NotNull Charge charge,
      @NotNull String price,
      @Min(1) @Max(Extra.MAX_QUANTITY) int maxQuantity,
      boolean active) {}

  record FeeRequest(@NotNull String amount) {}

  record ExtraResponse(
      String code,
      String name,
      ExtraKind kind,
      Charge charge,
      MoneyResponse price,
      int maxQuantity,
      boolean active) {

    static ExtraResponse of(Extra extra) {
      return new ExtraResponse(
          extra.code(),
          extra.name(),
          extra.kind(),
          extra.charge(),
          MoneyResponse.of(extra.price()),
          extra.maxQuantity(),
          extra.active());
    }
  }

  /** An amount as a decimal string, so that no client turns it into a floating-point number. */
  record MoneyResponse(String amount, String currency) {

    static MoneyResponse of(Money money) {
      return new MoneyResponse(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
  }
}
