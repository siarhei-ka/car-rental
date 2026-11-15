package psn.car.rental.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import psn.car.rental.inventory.application.InventoryService;
import psn.car.rental.inventory.domain.CarClassCode;
import psn.car.rental.inventory.domain.CarTypeCode;
import psn.car.rental.inventory.domain.HoldId;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.NoCarAvailableException;
import psn.car.rental.inventory.domain.RentalPeriod;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.tenant.Country;

/**
 * Proves NFR-1 against a real database: when many requests compete for the last cars at the same
 * moment, no more holds are accepted than there are cars.
 */
@IntegrationTest
@DisplayName("Concurrent requests")
class ConcurrencyTest {

  private static final int REQUESTS = 20;
  private static final Country POLAND = new Country("pl");

  @Autowired InventoryService inventory;
  @Autowired JdbcClient jdbc;

  @Test
  void onlyOneOfManyConcurrentRequestsGetsTheLastCar() throws Exception {
    StockKey key = key("cc-loc-1");
    inventory.changeStock(POLAND, key, 1);

    List<Outcome> outcomes =
        runConcurrently(i ->
            () ->
                inventory.placeHold(
                    POLAND,
                    key,
                    new HoldId("cc1-res-" + i),
                    new RentalPeriod(Instant.parse("2026-12-10T10:00:00Z"), 2),
                    key.location()));

    assertThat(outcomes).filteredOn(Outcome.HELD::equals).hasSize(1);
    assertThat(outcomes).filteredOn(Outcome.NO_CAR::equals).hasSize(REQUESTS - 1);
    assertThat(heldCars(key)).isEqualTo(1);
  }

  @Test
  void concurrentOverlappingAndOneWayRequestsNeverExceedTheStock() throws Exception {
    StockKey key = key("cc-loc-2");
    inventory.changeStock(POLAND, key, 3);

    List<Outcome> outcomes =
        runConcurrently(i ->
            () ->
                inventory.placeHold(
                    POLAND,
                    key,
                    new HoldId("cc2-res-" + i),
                    new RentalPeriod(
                        Instant.parse("2026-12-10T10:00:00Z").plusSeconds(3600L * i), 2),
                    i % 2 == 0 ? key.location() : new LocationId("cc-loc-elsewhere")));

    assertThat(outcomes).filteredOn(Outcome.HELD::equals).hasSize(3);
    assertThat(heldCars(key)).isEqualTo(3);
  }

  private int heldCars(StockKey key) {
    return jdbc.sql("select count(*) from tenant_pl.hold where location_id = :location")
        .param("location", key.location().value())
        .query(Integer.class)
        .single();
  }

  private List<Outcome> runConcurrently(Task task) throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Outcome>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(REQUESTS)) {
      for (int i = 0; i < REQUESTS; i++) {
        Callable<?> request = task.request(i);
        futures.add(
            pool.submit(() -> {
              start.await();
              try {
                request.call();
                return Outcome.HELD;
              } catch (NoCarAvailableException e) {
                return Outcome.NO_CAR;
              }
            }));
      }
      start.countDown();
      List<Outcome> outcomes = new ArrayList<>();
      for (Future<Outcome> future : futures) {
        outcomes.add(future.get());
      }
      return outcomes;
    }
  }

  private static StockKey key(String location) {
    return new StockKey(
        new LocationId(location), new CarTypeCode("SEDAN"), new CarClassCode("ECONOMY"));
  }

  private enum Outcome {
    HELD,
    NO_CAR
  }

  @FunctionalInterface
  private interface Task {
    Callable<?> request(int index);
  }
}
