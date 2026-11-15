package psn.car.rental.search.domain;

/** A car type or a car class, as the car type info service describes it. */
public record CarKind(String code, String name, boolean active) {}
