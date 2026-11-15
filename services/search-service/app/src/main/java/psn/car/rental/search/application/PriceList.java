package psn.car.rental.search.application;

import java.util.List;
import psn.car.rental.search.domain.DailyRate;

/** The pricing service, which owns the daily list prices. */
public interface PriceList {

  /** The daily rates at a location of a country. */
  List<DailyRate> rates(String country, String locationId);
}
