package psn.car.rental.search.application;

import java.util.List;
import psn.car.rental.search.domain.Availability;
import psn.car.rental.search.domain.SearchQuery;

/** The inventory service, which alone decides how many cars are free. */
public interface Inventory {

  List<Availability> availability(SearchQuery query);
}
