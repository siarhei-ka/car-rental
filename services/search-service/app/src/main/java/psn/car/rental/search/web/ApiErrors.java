package psn.car.rental.search.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.search.application.DependencyUnavailableException;
import psn.car.rental.search.domain.InvalidSearchException;
import psn.car.rental.shared.Problems;

/** Turns errors into Problem Details (NFR-22). */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidSearchException.class)
  ProblemDetail invalid(InvalidSearchException e) {
    return Problems.of(HttpStatus.BAD_REQUEST, "invalid-search", "Invalid search", e.getMessage());
  }

  @ExceptionHandler(DependencyUnavailableException.class)
  ProblemDetail unavailable(DependencyUnavailableException e) {
    return Problems.of(
        HttpStatus.SERVICE_UNAVAILABLE,
        "search-unavailable",
        "Search unavailable",
        e.getMessage() + ". Please try again.");
  }
}
