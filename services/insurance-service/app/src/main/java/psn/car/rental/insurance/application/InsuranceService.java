package psn.car.rental.insurance.application;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import psn.car.rental.insurance.domain.Driver;
import psn.car.rental.insurance.domain.InsuranceOption;
import psn.car.rental.insurance.domain.Money;
import psn.car.rental.insurance.tenant.Countries;
import psn.car.rental.insurance.tenant.Country;

/**
 * Insurance options with their prices. Every price comes from the provider, in the currency of the
 * rental country.
 */
@Service
public class InsuranceService {

  private final OptionStore options;
  private final InsuranceProvider provider;
  private final Countries countries;

  public InsuranceService(OptionStore options, InsuranceProvider provider, Countries countries) {
    this.options = options;
    this.provider = provider;
    this.countries = countries;
  }

  /** The options with the base price per day, which a visitor sees as a "from" price. */
  public List<PricedOption> optionsWithBasePrices(Country country) {
    String currency = countries.currencyOf(country);
    return options.all().stream()
        .map(option ->
            new PricedOption(
                option,
                provider.pricePerDay(option.code(), country.code(), currency, Optional.empty())))
        .toList();
  }

  /** The price per day of one option for a driver. */
  public PricedOption quote(Country country, String optionCode, Driver driver) {
    InsuranceOption option =
        options.all().stream()
            .filter(o -> o.code().equals(optionCode))
            .findFirst()
            .orElseThrow(() -> new UnknownOptionException(optionCode));
    return new PricedOption(
        option,
        provider.pricePerDay(
            option.code(), country.code(), countries.currencyOf(country), Optional.of(driver)));
  }

  public record PricedOption(InsuranceOption option, Money pricePerDay) {}
}
