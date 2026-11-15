package psn.car.rental.search.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Turns the answers of the other services into what a visitor may see. */
public final class Search {

  private Search() {}

  /**
   * Checks that both locations can be used for the query.
   *
   * @throws InvalidSearchException if a location does not exist, is not active, or the return
   *     location is in another country
   */
  public static void checkLocations(
      SearchQuery query, Optional<Location> pickUp, Optional<Location> returnTo) {
    Location from = pickUp.orElseThrow(() -> unknown(query.pickUpLocationId()));
    Location to = returnTo.orElseThrow(() -> unknown(query.returnLocationId()));
    if (!from.active() || !to.active()) {
      throw new InvalidSearchException("A location of the search is not open for rentals");
    }
    if (!from.country().equals(query.country()) || !to.country().equals(query.country())) {
      throw new InvalidSearchException(
          "The return location must be in the same country as the pick-up location");
    }
  }

  /**
   * The offers a visitor may choose: cars of active types and classes that match the query, have at
   * least one free car and a daily rate at the pick-up location, ordered by type and class name. A
   * car without a rate cannot be priced, so it is not offered.
   */
  public static List<Offer> offers(
      SearchQuery query,
      List<Availability> availability,
      List<CarKind> carTypes,
      List<CarKind> carClasses,
      List<DailyRate> rates) {
    Map<String, CarKind> types = byCode(carTypes);
    Map<String, CarKind> classes = byCode(carClasses);
    Map<String, Price> prices =
        rates.stream()
            .collect(
                Collectors.toMap(
                    r -> key(r.carType(), r.carClass()), DailyRate::price, (a, b) -> a));
    return availability.stream()
        .filter(a -> a.availableCars() > 0)
        .filter(a -> query.carType().map(a.carType()::equals).orElse(true))
        .filter(a -> query.carClass().map(a.carClass()::equals).orElse(true))
        .filter(a -> isActive(types.get(a.carType())) && isActive(classes.get(a.carClass())))
        .filter(a -> prices.containsKey(key(a.carType(), a.carClass())))
        .map(a ->
            new Offer(
                a.carType(),
                types.get(a.carType()).name(),
                a.carClass(),
                classes.get(a.carClass()).name(),
                a.availableCars(),
                prices.get(key(a.carType(), a.carClass()))))
        .sorted(
            Comparator.comparing(Offer::carTypeName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Offer::carClassName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  private static String key(String carType, String carClass) {
    return carType + "/" + carClass;
  }

  private static boolean isActive(CarKind kind) {
    return kind != null && kind.active();
  }

  private static Map<String, CarKind> byCode(List<CarKind> kinds) {
    return kinds.stream()
        .collect(Collectors.toMap(CarKind::code, Function.identity(), (a, b) -> a));
  }

  private static InvalidSearchException unknown(String id) {
    return new InvalidSearchException("Location " + id + " does not exist in this country");
  }
}
