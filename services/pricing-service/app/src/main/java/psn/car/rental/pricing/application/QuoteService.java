package psn.car.rental.pricing.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import psn.car.rental.pricing.application.Dependencies.Ancillaries;
import psn.car.rental.pricing.application.Dependencies.Discounts;
import psn.car.rental.pricing.application.Dependencies.DriverProfile;
import psn.car.rental.pricing.application.Dependencies.DriverProfiles;
import psn.car.rental.pricing.application.Dependencies.InsurancePrice;
import psn.car.rental.pricing.application.Dependencies.InsurancePrices;
import psn.car.rental.pricing.application.Dependencies.Location;
import psn.car.rental.pricing.application.Dependencies.Locations;
import psn.car.rental.pricing.application.Dependencies.OfferedExtra;
import psn.car.rental.pricing.config.PricingProperties;
import psn.car.rental.pricing.domain.DailyRate;
import psn.car.rental.pricing.domain.InvalidQuoteException;
import psn.car.rental.pricing.domain.LineKind;
import psn.car.rental.pricing.domain.Quote;
import psn.car.rental.pricing.domain.QuoteDiscount;
import psn.car.rental.pricing.domain.QuoteLine;
import psn.car.rental.pricing.domain.QuoteRefusedException;
import psn.car.rental.pricing.domain.QuoteRefusedException.Reason;
import psn.car.rental.pricing.domain.RateKey;
import psn.car.rental.pricing.domain.Rental;
import psn.car.rental.pricing.tenant.Countries;
import psn.car.rental.pricing.tenant.Country;
import psn.car.rental.shared.events.Analytics;

/**
 * Calculates the final price for a signed-in customer: the car at its daily rate, the insurance for
 * the driver, the extras, the one-way fee and the best discount. A quote reserves nothing and is
 * not stored. Prices sent by a browser are never used: every price comes from the services that own
 * it.
 */
@Service
public class QuoteService {

  private final RateStore rates;
  private final Countries countries;
  private final Locations locations;
  private final DriverProfiles profiles;
  private final InsurancePrices insurance;
  private final Ancillaries ancillaries;
  private final Discounts discounts;
  private final Clock clock;
  private final PricingProperties properties;
  private final Analytics analytics;

  public QuoteService(
      RateStore rates,
      Countries countries,
      Locations locations,
      DriverProfiles profiles,
      InsurancePrices insurance,
      Ancillaries ancillaries,
      Discounts discounts,
      Clock clock,
      PricingProperties properties,
      Analytics analytics) {
    this.rates = rates;
    this.countries = countries;
    this.locations = locations;
    this.profiles = profiles;
    this.insurance = insurance;
    this.ancillaries = ancillaries;
    this.discounts = discounts;
    this.clock = clock;
    this.properties = properties;
    this.analytics = analytics;
  }

  /**
   * The quote a customer asked for, recorded as the analytics event {@code price.quoted}. Quotes
   * that other services ask for are not recorded: the reservation events describe them.
   */
  public Quote quoteForCustomer(String customerId, QuoteRequest request) {
    Quote quote = quote(customerId, request);
    Map<String, Object> data = new java.util.LinkedHashMap<>();
    data.put("pickUpLocationId", request.pickUpLocationId());
    data.put(
        "returnLocationId",
        request.returnLocationId() == null
            ? request.pickUpLocationId()
            : request.returnLocationId());
    data.put("start", request.start().toString());
    data.put("days", request.days());
    data.put("carType", request.carType());
    data.put("carClass", request.carClass());
    data.put("insurance", request.insurance());
    data.put(
        "extras",
        request.extras().stream()
            .map(e -> Map.<String, Object>of("code", e.code(), "quantity", e.quantity()))
            .toList());
    data.put("subtotal", RateService.money(quote.subtotal()));
    data.put(
        "discount",
        quote
            .discount()
            .map(d ->
                Map.<String, Object>of("name", d.name(), "amount", RateService.money(d.amount())))
            .orElse(null));
    data.put("total", RateService.money(quote.total()));
    analytics.record(
        "price.quoted",
        countries.require(request.country()).code(),
        psn.car.rental.shared.events.Actor.customer(customerId),
        data);
    return quote;
  }

  /**
   * @throws InvalidQuoteException if the request breaks a rule
   * @throws QuoteRefusedException if the rental is not offered as asked
   * @throws ProfileMissingException if the customer has no driver profile
   * @throws DependencyUnavailableException if a service the price depends on fails
   */
  public Quote quote(String customerId, QuoteRequest request) {
    Country country = countries.require(request.country());
    String code = country.code();
    Rental rental = new Rental(request.start(), request.days());
    rental.checkLimits(clock.instant(), properties.maxDays());
    Set<String> codes = new HashSet<>();
    for (ExtraChoice choice : request.extras()) {
      if (!codes.add(choice.code())) {
        throw new InvalidQuoteException("The extra " + choice.code() + " is listed twice");
      }
    }

    Location pickUp = location(code, request.pickUpLocationId(), "pick-up");
    String returnId = request.returnLocationId() == null ? pickUp.id() : request.returnLocationId();
    boolean oneWay = !returnId.equals(pickUp.id());
    Location returnTo = oneWay ? location(code, returnId, "return") : pickUp;
    if (!pickUp.active() || !returnTo.active()) {
      throw new QuoteRefusedException(
          Reason.LOCATION_NOT_OFFERED, "The location is not offered for new rentals");
    }
    ZoneId zone = ZoneId.of(pickUp.timeZone());

    RateKey key = new RateKey(pickUp.id(), request.carType(), request.carClass());
    DailyRate rate =
        rates.list(country, Optional.of(pickUp.id())).stream()
            .filter(r -> r.key().equals(key))
            .findFirst()
            .orElseThrow(() ->
                new QuoteRefusedException(
                    Reason.CAR_NOT_OFFERED,
                    "This car type and class are not offered at the pick-up location"));

    DriverProfile driver = profiles.find(customerId).orElseThrow(ProfileMissingException::new);
    int age = rental.driverAge(driver.dateOfBirth(), zone, properties.minimumDriverAge());

    List<QuoteLine> lines = new ArrayList<>();
    lines.add(QuoteLine.carRental(rate, rental.days()));
    InsurancePrice cover = insurance.quote(code, request.insurance(), age, driver.licenceCountry());
    lines.add(QuoteLine.insurance(cover.code(), cover.name(), cover.pricePerDay(), rental.days()));
    lines.addAll(extras(code, request.extras(), rental.days()));
    if (oneWay) {
      lines.add(
          QuoteLine.oneWayFee(
              ancillaries
                  .oneWayFee(code)
                  .orElseThrow(() ->
                      new QuoteRefusedException(
                          Reason.ONE_WAY_NOT_OFFERED,
                          "Returning the car to another location is not offered in this"
                              + " country"))));
    }

    Currency currency = Currency.getInstance(countries.currencyOf(country));
    Quote before = new Quote(currency, lines, Optional.empty());
    LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
    Optional<QuoteDiscount> discount =
        discounts.best(
            code, today, rental.days(), before.subtotal(), before.linesOf(LineKind.EXTRA));
    return new Quote(currency, lines, discount);
  }

  private Location location(String country, String id, String role) {
    return locations
        .find(country, id)
        .orElseThrow(() ->
            new InvalidQuoteException(
                "The " + role + " location " + id + " does not exist in this country"));
  }

  private List<QuoteLine> extras(String country, List<ExtraChoice> choices, int days) {
    if (choices.isEmpty()) {
      return List.of();
    }
    Map<String, OfferedExtra> offered =
        ancillaries.extras(country).stream()
            .collect(Collectors.toMap(OfferedExtra::code, Function.identity()));
    return choices.stream()
        .map(choice -> {
          OfferedExtra extra = offered.get(choice.code());
          if (extra == null) {
            throw new InvalidQuoteException("The extra " + choice.code() + " is not offered");
          }
          return QuoteLine.extra(
              extra.code(),
              extra.name(),
              extra.charge(),
              extra.price(),
              extra.maxQuantity(),
              choice.quantity(),
              days);
        })
        .toList();
  }

  /**
   * What the customer asks to be priced.
   *
   * @param returnLocationId the return location, or null for the pick-up location
   * @param insurance the code of the insurance option
   */
  public record QuoteRequest(
      String country,
      String pickUpLocationId,
      String returnLocationId,
      Instant start,
      int days,
      String carType,
      String carClass,
      String insurance,
      List<ExtraChoice> extras) {

    public QuoteRequest {
      extras = extras == null ? List.of() : List.copyOf(extras);
    }
  }

  /** An extra the customer chose, and how many. */
  public record ExtraChoice(String code, int quantity) {}
}
