package psn.car.rental.administration.application;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import psn.car.rental.administration.config.AdministrationProperties;
import psn.car.rental.administration.domain.CarClass;
import psn.car.rental.administration.domain.CarType;
import psn.car.rental.administration.domain.Country;
import psn.car.rental.administration.domain.Location;
import psn.car.rental.administration.tenant.SchemaMigrations;
import psn.car.rental.shared.events.Analytics;
import psn.car.rental.shared.events.Outbox;

/**
 * Use cases of reference data. Every change that alters data is written together with an event that
 * carries the changed data, so that other services can keep their copies up to date, and with an
 * analytics event of the administrator's action.
 */
@Service
public class ReferenceDataService {

  private final ReferenceDataStore store;
  private final Outbox outbox;
  private final Analytics analytics;
  private final TransactionTemplate transactions;
  private final SchemaMigrations migrations;
  private final Clock clock;
  private final String topic;

  public ReferenceDataService(
      ReferenceDataStore store,
      Outbox outbox,
      Analytics analytics,
      TransactionTemplate transactions,
      SchemaMigrations migrations,
      Clock clock,
      AdministrationProperties properties) {
    this.store = store;
    this.outbox = outbox;
    this.analytics = analytics;
    this.transactions = transactions;
    this.migrations = migrations;
    this.clock = clock;
    this.topic = properties.referenceDataTopic();
  }

  public List<Country> countries() {
    return inTransaction(store::countries);
  }

  /** Creates or changes a country. A new country gets its own database schema. */
  public Saved<Country> saveCountry(Country country) {
    Saved<Country> saved =
        save(
            () -> store.country(country.code()),
            country,
            store::saveCountry,
            "COUNTRY",
            "country:" + country.code(),
            null,
            Map.of(
                "code", country.code(),
                "name", country.name(),
                "currency", country.currency(),
                "active", country.active()));
    if (saved.created()) {
      migrations.migrateCountry(country.code());
    }
    return saved;
  }

  public List<CarType> carTypes() {
    return inTransaction(store::carTypes);
  }

  public Saved<CarType> saveCarType(CarType carType) {
    return save(
        () -> store.carType(carType.code()),
        carType,
        store::saveCarType,
        "CAR_TYPE",
        "car-type:" + carType.code(),
        null,
        Map.of("code", carType.code(), "name", carType.name(), "active", carType.active()));
  }

  public List<CarClass> carClasses() {
    return inTransaction(store::carClasses);
  }

  public Saved<CarClass> saveCarClass(CarClass carClass) {
    return save(
        () -> store.carClass(carClass.code()),
        carClass,
        store::saveCarClass,
        "CAR_CLASS",
        "car-class:" + carClass.code(),
        null,
        Map.of("code", carClass.code(), "name", carClass.name(), "active", carClass.active()));
  }

  public List<Location> locations(String country) {
    return inTransaction(() -> {
      requireCountry(country);
      return store.locations(country);
    });
  }

  public Location location(String country, String id) {
    return inTransaction(() -> {
      requireCountry(country);
      return store
          .location(country, id)
          .orElseThrow(() -> new NotFoundException("Location " + id + " does not exist"));
    });
  }

  /** Creates or changes a location of an existing country. */
  public Saved<Location> saveLocation(Location location) {
    return inTransaction(() -> {
      requireCountry(location.country());
      return save(
          () -> store.location(location.country(), location.id()),
          location,
          store::saveLocation,
          "LOCATION",
          "location:" + location.country() + ":" + location.id(),
          location.country(),
          locationData(location));
    });
  }

  /** Everything at once, for a service that loads its copy at startup. */
  public Snapshot snapshot() {
    return inTransaction(() -> {
      List<Country> countries = store.countries();
      List<Location> locations =
          countries.stream().flatMap(c -> store.locations(c.code()).stream()).toList();
      return new Snapshot(countries, store.carTypes(), store.carClasses(), locations);
    });
  }

  /** All reference data, as loaded by other services at startup. */
  public record Snapshot(
      List<Country> countries,
      List<CarType> carTypes,
      List<CarClass> carClasses,
      List<Location> locations) {}

  static Map<String, Object> locationData(Location location) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("id", location.id());
    data.put("country", location.country());
    data.put("name", location.name());
    data.put("city", location.city());
    data.put("latitude", location.latitude());
    data.put("longitude", location.longitude());
    data.put("timeZone", location.timeZone().getId());
    data.put("active", location.active());
    return data;
  }

  private void requireCountry(String code) {
    if (code == null || store.country(code).isEmpty()) {
      throw new UnknownCountryException("Country " + code + " does not exist");
    }
  }

  /**
   * Saves a value and records an event, unless the value is unchanged. Joins the transaction of the
   * caller if there is one.
   */
  private <T> Saved<T> save(
      Supplier<Optional<T>> current,
      T value,
      Consumer<T> write,
      String kind,
      String key,
      String country,
      Map<String, Object> data) {
    return inTransaction(() -> {
      Optional<T> before = current.get();
      if (before.isPresent() && before.get().equals(value)) {
        return new Saved<>(value, false);
      }
      write.accept(value);
      Map<String, Object> event = new LinkedHashMap<>();
      event.put("eventId", UUID.randomUUID().toString());
      event.put("kind", kind);
      event.put("action", "SAVED");
      event.put("occurredAt", clock.instant().toString());
      if (country != null) {
        event.put("country", country);
      }
      event.put("data", data);
      outbox.add(topic, key, event);
      analytics.record(
          "admin.reference-data-changed",
          country,
          Map.of("kind", kind, "created", before.isEmpty(), "data", data));
      return new Saved<>(value, before.isEmpty());
    });
  }

  private <T> T inTransaction(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }
}
