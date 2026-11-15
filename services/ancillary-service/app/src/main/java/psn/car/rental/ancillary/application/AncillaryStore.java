package psn.car.rental.ancillary.application;

import java.util.List;
import java.util.Optional;
import psn.car.rental.ancillary.domain.Extra;
import psn.car.rental.ancillary.domain.Money;
import psn.car.rental.ancillary.tenant.Country;

/** Where the extras and the one-way fee of each country are kept. */
public interface AncillaryStore {

  /** The extras of a country ordered by name, all or only those that are offered. */
  List<Extra> extras(Country country, boolean activeOnly);

  /** Creates the extra, or replaces the extra with the same code. */
  void save(Country country, Extra extra);

  Optional<Money> oneWayFee(Country country);

  void saveOneWayFee(Country country, Money fee);
}
