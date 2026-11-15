package psn.car.rental.search.domain;

/** A rental location, as the location info service describes it. */
public record Location(String id, String country, String name, String timeZone, boolean active) {}
