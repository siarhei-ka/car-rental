package psn.car.rental.inventory.application;

import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.domain.Transfer;

/**
 * A transfer together with its source stock row.
 *
 * @param created false when an identical request had already created the transfer
 */
public record PlacedTransfer(StockKey key, Transfer transfer, boolean created) {}
