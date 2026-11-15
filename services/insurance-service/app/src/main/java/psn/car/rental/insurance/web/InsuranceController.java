package psn.car.rental.insurance.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.insurance.application.InsuranceService;
import psn.car.rental.insurance.application.InsuranceService.PricedOption;
import psn.car.rental.insurance.domain.Driver;
import psn.car.rental.insurance.domain.Money;
import psn.car.rental.insurance.tenant.Countries;

/**
 * Insurance options with their prices per day. The options with their base prices are public; the
 * price for a driver is asked by the pricing service, which knows the driver.
 */
@RestController
class InsuranceController {

  private final InsuranceService insurance;
  private final Countries countries;

  InsuranceController(InsuranceService insurance, Countries countries) {
    this.insurance = insurance;
    this.countries = countries;
  }

  /** Every option with its base price per day, which a visitor sees as a "from" price. */
  @GetMapping("/api/v1/insurance-options")
  List<OptionResponse> options(
      @RequestHeader(name = "X-Country", required = false) String country) {
    return insurance.optionsWithBasePrices(countries.require(country)).stream()
        .map(OptionResponse::of)
        .toList();
  }

  /** The price per day of an option for a driver. */
  @PostMapping("/api/v1/insurance-quotes")
  OptionResponse quote(
      @RequestHeader(name = "X-Country", required = false) String country,
      @Valid @RequestBody QuoteRequest request) {
    return OptionResponse.of(
        insurance.quote(
            countries.require(country),
            request.option(),
            new Driver(request.driverAge(), request.licenceCountry())));
  }

  /**
   * @param driverAge the age of the driver on the start of the rental
   */
  record QuoteRequest(
      @NotNull String option, @NotNull Integer driverAge, @NotNull String licenceCountry) {}

  record OptionResponse(
      String code,
      String name,
      String description,
      boolean preselected,
      MoneyResponse pricePerDay) {

    static OptionResponse of(PricedOption priced) {
      return new OptionResponse(
          priced.option().code(),
          priced.option().name(),
          priced.option().description(),
          priced.option().preselected(),
          MoneyResponse.of(priced.pricePerDay()));
    }
  }

  /** An amount as a decimal string, so that no client turns it into a floating-point number. */
  record MoneyResponse(String amount, String currency) {

    static MoneyResponse of(Money money) {
      return new MoneyResponse(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
  }
}
