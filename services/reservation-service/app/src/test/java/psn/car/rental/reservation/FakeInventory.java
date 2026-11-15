package psn.car.rental.reservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import psn.car.rental.reservation.application.ConflictingRequestException;
import psn.car.rental.reservation.application.HoldNotFoundException;
import psn.car.rental.reservation.application.Inventory;
import psn.car.rental.reservation.application.InventoryUnavailableException;
import psn.car.rental.reservation.application.NoCarAvailableException;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Country;

/**
 * Stands in for the inventory service. It counts holds per car type and location, ignoring time,
 * which is enough to test how reservations react to each answer. The capacity rule itself is tested
 * in the inventory service.
 */
public class FakeInventory implements Inventory {

  /** What a call should do instead of succeeding. */
  public enum Failure {
    NONE,
    UNAVAILABLE,
    NOT_FOUND
  }

  private final Map<String, RentalRequest> holds = new ConcurrentHashMap<>();
  private final Map<String, Integer> capacity = new ConcurrentHashMap<>();
  private final List<String> calls = new CopyOnWriteArrayList<>();
  private volatile Failure onPlace = Failure.NONE;
  private volatile Failure onConfirm = Failure.NONE;
  private volatile Failure onRelease = Failure.NONE;
  private volatile Failure onReturn = Failure.NONE;

  public void reset() {
    holds.clear();
    capacity.clear();
    calls.clear();
    onPlace = onConfirm = onRelease = onReturn = Failure.NONE;
  }

  public void setCars(String country, String location, String carType, int cars) {
    capacity.put(country + "|" + location + "|" + carType, cars);
  }

  public void failPlace(Failure failure) {
    onPlace = failure;
  }

  public void failConfirm(Failure failure) {
    onConfirm = failure;
  }

  public void failRelease(Failure failure) {
    onRelease = failure;
  }

  public void failReturn(Failure failure) {
    onReturn = failure;
  }

  /** Calls in the order they arrived, as {@code action:reservationId}. */
  public List<String> calls() {
    return new ArrayList<>(calls);
  }

  public boolean holds(ReservationId id) {
    return holds.keySet().stream().anyMatch(key -> key.endsWith("|" + id));
  }

  @Override
  public synchronized void placeHold(Country country, ReservationId id, RentalRequest rental) {
    calls.add("place:" + id);
    fail(onPlace);
    String holdKey = country.code() + "|" + id;
    RentalRequest existing = holds.get(holdKey);
    if (existing != null) {
      if (!existing.equals(rental)) {
        throw new ConflictingRequestException("The hold exists for a different rental");
      }
      return;
    }
    String carsKey = country.code() + "|" + rental.pickUpLocationId() + "|" + rental.carType();
    long held =
        holds.entrySet().stream()
            .filter(e -> e.getKey().startsWith(country.code() + "|"))
            .map(Map.Entry::getValue)
            .filter(r -> r.pickUpLocationId().equals(rental.pickUpLocationId()))
            .filter(r -> r.carType().equals(rental.carType()))
            .count();
    if (held >= capacity.getOrDefault(carsKey, 0)) {
      throw new NoCarAvailableException("No car is free");
    }
    holds.put(holdKey, rental);
  }

  @Override
  public void confirmHold(Country country, ReservationId id) {
    calls.add("confirm:" + id);
    fail(onConfirm);
    if (!holds.containsKey(country.code() + "|" + id)) {
      throw new HoldNotFoundException("No hold");
    }
  }

  @Override
  public void releaseHold(Country country, ReservationId id) {
    calls.add("release:" + id);
    fail(onRelease);
    holds.remove(country.code() + "|" + id);
  }

  @Override
  public void registerReturn(Country country, ReservationId id) {
    calls.add("return:" + id);
    fail(onReturn);
    if (holds.remove(country.code() + "|" + id) == null) {
      throw new HoldNotFoundException("No hold");
    }
  }

  private static void fail(Failure failure) {
    switch (failure) {
      case UNAVAILABLE -> throw new InventoryUnavailableException("The fake inventory is down");
      case NOT_FOUND -> throw new HoldNotFoundException("The fake inventory has no hold");
      case NONE -> {}
    }
  }
}
