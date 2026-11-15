package psn.car.rental.inventory.application;

import psn.car.rental.inventory.domain.Hold;
import psn.car.rental.inventory.domain.StockKey;

/**
 * A hold together with its stock row.
 *
 * @param created false when an identical request had already created the hold
 */
public record PlacedHold(StockKey key, Hold hold, boolean created) {}
