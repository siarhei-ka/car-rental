package psn.car.rental.search.domain;

/** The daily list price of a car type and class at the pick-up location. */
public record DailyRate(String carType, String carClass, Price price) {}
