package psn.car.rental.search.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Search")
class SearchTest {

  private static final Instant NOW = Instant.parse("2026-12-01T08:00:00Z");
  private static final Instant START = Instant.parse("2026-12-10T10:00:00Z");
  private static final Location WAW = new Location("waw", "pl", "Warsaw", "Europe/Warsaw", true);
  private static final Location KRK = new Location("krk", "pl", "Krakow", "Europe/Warsaw", true);
  private static final List<CarKind> TYPES =
      List.of(
          new CarKind("SEDAN", "Sedan", true),
          new CarKind("SUV", "SUV", true),
          new CarKind("CABRIO", "Cabrio", false));
  private static final List<CarKind> CLASSES =
      List.of(new CarKind("ECONOMY", "Economy", true), new CarKind("PREMIUM", "Premium", true));

  private static SearchQuery query(Optional<String> type, Optional<String> carClass) {
    return new SearchQuery("pl", "waw", "krk", START, 3, type, carClass);
  }

  @Test
  void validQueryHasAnEnd() {
    SearchQuery query = query(Optional.empty(), Optional.empty());

    assertThatCode(() -> query.checkLimits(NOW, 30)).doesNotThrowAnyException();
    assertThat(query.end()).isEqualTo(Instant.parse("2026-12-13T10:00:00Z"));
  }

  @Test
  void appliesTheLimitsOfAReservation() {
    assertThatThrownBy(() ->
            new SearchQuery("pl", "waw", "waw", NOW, 1, Optional.empty(), Optional.empty())
                .checkLimits(NOW, 30))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() ->
            new SearchQuery("pl", "waw", "waw", START, 31, Optional.empty(), Optional.empty())
                .checkLimits(NOW, 30))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() ->
            new SearchQuery("pl", "waw", "waw", START, 0, Optional.empty(), Optional.empty())
                .checkLimits(NOW, 30))
        .isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void rejectsInvalidValues() {
    assertThatThrownBy(
            () -> new SearchQuery("PL", "waw", "krk", START, 1, Optional.empty(), Optional.empty()))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(
            () -> new SearchQuery("pl", "w w", "krk", START, 1, Optional.empty(), Optional.empty()))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> query(Optional.of("sedan"), Optional.empty()))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() -> query(Optional.empty(), Optional.of("")))
        .isInstanceOf(InvalidSearchException.class);
  }

  @Test
  void locationsMustExistBeActiveAndBeInTheCountry() {
    SearchQuery query = query(Optional.empty(), Optional.empty());

    assertThatCode(() -> Search.checkLocations(query, Optional.of(WAW), Optional.of(KRK)))
        .doesNotThrowAnyException();
    assertThatThrownBy(() -> Search.checkLocations(query, Optional.empty(), Optional.of(KRK)))
        .isInstanceOf(InvalidSearchException.class)
        .hasMessageContaining("waw");
    assertThatThrownBy(() -> Search.checkLocations(query, Optional.of(WAW), Optional.empty()))
        .isInstanceOf(InvalidSearchException.class);
    assertThatThrownBy(() ->
            Search.checkLocations(
                query,
                Optional.of(WAW),
                Optional.of(new Location("krk", "pl", "Krakow", "Europe/Warsaw", false))))
        .isInstanceOf(InvalidSearchException.class)
        .hasMessageContaining("not open");
    assertThatThrownBy(() ->
            Search.checkLocations(
                query,
                Optional.of(WAW),
                Optional.of(new Location("krk", "de", "Krakow", "Europe/Berlin", true))))
        .isInstanceOf(InvalidSearchException.class)
        .hasMessageContaining("same country");
  }

  @Test
  void offersOnlyFreeCarsOfActiveTypesAndClassesOrderedByName() {
    List<Availability> availability =
        List.of(
            new Availability("SUV", "ECONOMY", 2),
            new Availability("SEDAN", "PREMIUM", 1),
            new Availability("SEDAN", "ECONOMY", 0),
            new Availability("CABRIO", "ECONOMY", 5),
            new Availability("VAN", "ECONOMY", 3));

    List<Offer> offers =
        Search.offers(
            query(Optional.empty(), Optional.empty()), availability, TYPES, CLASSES, RATES);

    assertThat(offers)
        .containsExactly(
            new Offer("SEDAN", "Sedan", "PREMIUM", "Premium", 1, price("299.00")),
            new Offer("SUV", "SUV", "ECONOMY", "Economy", 2, price("219.00")));
  }

  @Test
  void offersOnlyCarsWithADailyRate() {
    List<Availability> availability =
        List.of(new Availability("SUV", "ECONOMY", 2), new Availability("SEDAN", "ECONOMY", 1));

    assertThat(
            Search.offers(
                query(Optional.empty(), Optional.empty()),
                availability,
                TYPES,
                CLASSES,
                List.of(new DailyRate("SUV", "ECONOMY", price("219.00")))))
        .extracting(Offer::carType)
        .containsExactly("SUV");
  }

  private static Price price(String amount) {
    return new Price(amount, "PLN");
  }

  private static final List<DailyRate> RATES =
      List.of(
          new DailyRate("SEDAN", "ECONOMY", new Price("149.00", "PLN")),
          new DailyRate("SEDAN", "PREMIUM", new Price("299.00", "PLN")),
          new DailyRate("SUV", "ECONOMY", new Price("219.00", "PLN")),
          new DailyRate("VAN", "ECONOMY", new Price("259.00", "PLN")),
          new DailyRate("CABRIO", "ECONOMY", new Price("999.00", "PLN")));

  @Test
  void filtersByTheRequestedTypeAndClass() {
    List<Availability> availability =
        List.of(
            new Availability("SEDAN", "ECONOMY", 1),
            new Availability("SEDAN", "PREMIUM", 1),
            new Availability("SUV", "ECONOMY", 1));

    assertThat(
            Search.offers(
                query(Optional.of("SEDAN"), Optional.empty()), availability, TYPES, CLASSES, RATES))
        .extracting(Offer::carClass)
        .containsExactly("ECONOMY", "PREMIUM");
    assertThat(
            Search.offers(
                query(Optional.of("SEDAN"), Optional.of("PREMIUM")),
                availability,
                TYPES,
                CLASSES,
                RATES))
        .hasSize(1);
    assertThat(
            Search.offers(
                query(Optional.empty(), Optional.of("ECONOMY")),
                availability,
                TYPES,
                CLASSES,
                RATES))
        .extracting(Offer::carType)
        .containsExactly("SEDAN", "SUV");
  }
}
