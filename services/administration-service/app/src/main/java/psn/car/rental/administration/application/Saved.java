package psn.car.rental.administration.application;

/**
 * The result of saving reference data.
 *
 * @param created true if it did not exist before
 */
public record Saved<T>(T value, boolean created) {}
