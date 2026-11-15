package psn.car.rental.inventory.application;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import psn.car.rental.inventory.domain.HoldId;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.tenant.Countries;
import psn.car.rental.inventory.tenant.Country;

/**
 * Releases provisional holds that were not confirmed in time, so that a crash between placing a
 * hold and storing the reservation cannot keep a car blocked.
 */
@Component
public class HoldExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

  private final InventoryService inventory;
  private final Countries countries;

  public HoldExpiryJob(InventoryService inventory, Countries countries) {
    this.inventory = inventory;
    this.countries = countries;
  }

  @Scheduled(fixedDelayString = "${inventory.hold-expiry-check:PT30S}")
  public void releaseExpiredHolds() {
    for (Country country : countries.all()) {
      for (StockKey key : inventory.stockWithExpiredHolds(country)) {
        List<HoldId> released = inventory.expireHolds(country, key);
        if (!released.isEmpty()) {
          log.info("Released {} expired holds in {}", released.size(), country.code());
        }
      }
    }
  }
}
