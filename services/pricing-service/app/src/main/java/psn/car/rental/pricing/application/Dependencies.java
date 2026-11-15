package psn.car.rental.pricing.application;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import psn.car.rental.pricing.domain.Charge;
import psn.car.rental.pricing.domain.Money;
import psn.car.rental.pricing.domain.QuoteDiscount;
import psn.car.rental.pricing.domain.QuoteLine;

/**
 * The services a quote depends on. Each call has a time limit; a service that does not answer in
 * time, or fails, raises {@link DependencyUnavailableException}.
 */
public final class Dependencies {

  private Dependencies() {}

  /** The location info service. */
  public interface Locations {

    /** The location with this ID in the country, or empty if the country has no such location. */
    Optional<Location> find(String country, String id);
  }

  /** A rental location, as the location info service describes it. */
  public record Location(String id, String country, String timeZone, boolean active) {}

  /** The customer service. */
  public interface DriverProfiles {

    /** The driver profile of a customer, or empty if the customer has not entered one. */
    Optional<DriverProfile> find(String customerId);
  }

  /** What the price depends on about the driver. */
  public record DriverProfile(LocalDate dateOfBirth, String licenceCountry) {}

  /** The insurance service. */
  public interface InsurancePrices {

    /**
     * The price per day of an insurance option for the driver.
     *
     * @throws psn.car.rental.pricing.domain.InvalidQuoteException if there is no such option
     */
    InsurancePrice quote(String country, String option, int driverAge, String licenceCountry);
  }

  /** An insurance option with its price per day for the driver. */
  public record InsurancePrice(String code, String name, Money pricePerDay) {}

  /** The ancillary service. */
  public interface Ancillaries {

    /** The extras on offer in the country. */
    List<OfferedExtra> extras(String country);

    /** The fee for returning the car to another location, or empty if the country has none. */
    Optional<Money> oneWayFee(String country);
  }

  /** An extra on offer, with its list price. */
  public record OfferedExtra(
      String code, String name, Charge charge, Money price, int maxQuantity) {}

  /** The discount service. */
  public interface Discounts {

    /**
     * The discount that saves the customer the most, or empty.
     *
     * @param day the day of the calculation, in the time zone of the pick-up location
     * @param total every line of the quote added up
     */
    Optional<QuoteDiscount> best(
        String country, LocalDate day, int days, Money total, List<QuoteLine> extras);
  }
}
