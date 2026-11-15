package psn.car.rental.search.domain;

/**
 * A car type and class a visitor can choose, with the number of free cars and the daily list price
 * at the pick-up location.
 */
public record Offer(
    String carType,
    String carTypeName,
    String carClass,
    String carClassName,
    int availableCars,
    Price dailyRate) {}
