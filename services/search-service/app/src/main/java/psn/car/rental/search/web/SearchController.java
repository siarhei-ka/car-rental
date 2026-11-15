package psn.car.rental.search.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.search.application.SearchService;
import psn.car.rental.search.domain.SearchQuery;
import psn.car.rental.search.domain.SearchResult;

/** The search, for anyone. Nothing is reserved by a search. */
@RestController
class SearchController {

  private final SearchService search;

  SearchController(SearchService search) {
    this.search = search;
  }

  @GetMapping("/api/v1/search")
  SearchResult search(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam String pickUpLocationId,
      @RequestParam(required = false) String returnLocationId,
      @RequestParam Instant start,
      @RequestParam @Min(1) @Max(366) int days,
      @RequestParam(required = false) String carType,
      @RequestParam(required = false) String carClass) {
    return search.search(
        new SearchQuery(
            country,
            pickUpLocationId,
            returnLocationId == null ? pickUpLocationId : returnLocationId,
            start,
            days,
            Optional.ofNullable(carType),
            Optional.ofNullable(carClass)));
  }
}
