package psn.car.rental.search.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import psn.car.rental.search.SearchProperties;
import psn.car.rental.search.domain.Location;
import psn.car.rental.search.domain.Search;
import psn.car.rental.search.domain.SearchQuery;
import psn.car.rental.search.domain.SearchResult;
import psn.car.rental.shared.events.Actor;
import psn.car.rental.shared.events.Analytics;

/**
 * Answers a search: checks the query and the locations, asks the inventory how many cars are free
 * and the pricing service for the daily rates, and keeps the offers a visitor may choose. Nothing
 * is stored.
 */
@Service
public class SearchService {

  private final Locations locations;
  private final CarCatalog catalog;
  private final Inventory inventory;
  private final PriceList prices;
  private final Clock clock;
  private final int maxDays;
  private final Analytics analytics;

  public SearchService(
      Locations locations,
      CarCatalog catalog,
      Inventory inventory,
      PriceList prices,
      Clock clock,
      SearchProperties properties,
      Analytics analytics) {
    this.locations = locations;
    this.catalog = catalog;
    this.inventory = inventory;
    this.prices = prices;
    this.clock = clock;
    this.maxDays = properties.maxDays();
    this.analytics = analytics;
  }

  public SearchResult search(SearchQuery query) {
    query.checkLimits(clock.instant(), maxDays);
    var pickUp = locations.find(query.country(), query.pickUpLocationId());
    var returnTo =
        query.returnLocationId().equals(query.pickUpLocationId())
            ? pickUp
            : locations.find(query.country(), query.returnLocationId());
    Search.checkLocations(query, pickUp, returnTo);
    var offers =
        Search.offers(
            query,
            inventory.availability(query),
            catalog.carTypes(),
            catalog.carClasses(),
            prices.rates(query.country(), query.pickUpLocationId()));
    Location from = pickUp.orElseThrow();
    SearchResult result =
        new SearchResult(
            from, returnTo.orElseThrow(), query.start(), query.days(), query.end(), offers);
    record(query, result);
    return result;
  }

  /** The analytics event of a search. It names no customer, and it never delays the answer. */
  private void record(SearchQuery query, SearchResult result) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("pickUpLocationId", query.pickUpLocationId());
    data.put("returnLocationId", query.returnLocationId());
    data.put("start", query.start().toString());
    data.put("days", query.days());
    data.put("carType", query.carType().orElse(null));
    data.put("carClass", query.carClass().orElse(null));
    data.put("offers", result.offers().size());
    analytics.record("search.performed", query.country(), Actor.VISITOR, data);
  }
}
