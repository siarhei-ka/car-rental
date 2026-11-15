package psn.car.rental.search.domain;

/** How many cars of a type and class the inventory could still hold for the query. */
public record Availability(String carType, String carClass, int availableCars) {}
