package psn.car.rental.inventory.application;

import psn.car.rental.inventory.domain.StockKey;

/** How many cars of a type and class could still be held for a requested rental. */
public record AvailableCars(StockKey key, int cars) {}
