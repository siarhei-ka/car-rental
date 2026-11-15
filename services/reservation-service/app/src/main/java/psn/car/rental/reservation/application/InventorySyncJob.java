package psn.car.rental.reservation.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Countries;
import psn.car.rental.reservation.tenant.Country;

/**
 * Retries inventory updates that failed or were interrupted, for example when the inventory service
 * was down or this service stopped between storing a change and telling the inventory.
 */
@Component
public class InventorySyncJob {

  private final ReservationService reservations;
  private final Countries countries;

  public InventorySyncJob(ReservationService reservations, Countries countries) {
    this.reservations = reservations;
    this.countries = countries;
  }

  @Scheduled(fixedDelayString = "${reservation.inventory-sync-check:PT15S}")
  public void retryPendingUpdates() {
    for (Country country : countries.all()) {
      for (ReservationId id : reservations.pendingInventoryUpdates(country)) {
        reservations.syncInventory(country, id);
      }
    }
  }
}
