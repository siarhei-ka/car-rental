package psn.car.rental.search.domain;

import java.time.Instant;
import java.util.List;

/** The answer to a search. */
public record SearchResult(
    Location pickUpLocation,
    Location returnLocation,
    Instant start,
    int days,
    Instant end,
    List<Offer> offers) {}
