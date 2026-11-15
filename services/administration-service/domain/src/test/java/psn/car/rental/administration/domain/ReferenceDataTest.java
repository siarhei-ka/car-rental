package psn.car.rental.administration.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Reference data")
class ReferenceDataTest {

  private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

  @Test
  void acceptsValidReferenceData() {
    Country poland = new Country("pl", " Poland ", "PLN", true);
    Location airport =
        new Location("waw-airport", "pl", "Warsaw Airport", "Warsaw", 52.17, 20.97, WARSAW, true);

    assertThat(poland.name()).isEqualTo("Poland");
    assertThat(airport.timeZone()).isEqualTo(WARSAW);
    assertThat(new CarType("SEDAN", "Sedan", true).code()).isEqualTo("SEDAN");
    assertThat(new CarClass("ECONOMY", "Economy", false).active()).isFalse();
  }

  @Test
  void rejectsInvalidCountries() {
    assertThatThrownBy(() -> new Country("PL", "Poland", "PLN", true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Country("pl", "Poland", "ZZZ", true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Country("pl", "Poland", null, true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Country("pl", " ", "PLN", true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Country("pl", "x".repeat(101), "PLN", true))
        .isInstanceOf(InvalidReferenceDataException.class);
  }

  @Test
  void rejectsInvalidCodes() {
    assertThatThrownBy(() -> new CarType("sedan", "Sedan", true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new CarClass("", "Economy", true))
        .isInstanceOf(InvalidReferenceDataException.class);
  }

  @Test
  void rejectsInvalidLocations() {
    assertThatThrownBy(() -> new Location("bad id", "pl", "Name", "City", 0, 0, WARSAW, true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Location("a", "pl", "Name", "City", 91, 0, WARSAW, true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Location("a", "pl", "Name", "City", 0, -181, WARSAW, true))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> new Location("a", "pl", "Name", "City", Double.NaN, 0, WARSAW, true))
        .isInstanceOf(InvalidReferenceDataException.class);
  }

  @Test
  void readsOnlyRegionTimeZones() {
    assertThat(Location.timeZone("Europe/Berlin")).isEqualTo(ZoneId.of("Europe/Berlin"));
    assertThatThrownBy(() -> Location.timeZone("UTC+2"))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> Location.timeZone("Europe/Nowhere"))
        .isInstanceOf(InvalidReferenceDataException.class);
    assertThatThrownBy(() -> Location.timeZone(null))
        .isInstanceOf(InvalidReferenceDataException.class);
  }
}
