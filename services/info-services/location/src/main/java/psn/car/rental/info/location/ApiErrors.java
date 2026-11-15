package psn.car.rental.info.location;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.shared.Problems;

/** Turns errors into Problem Details (NFR-22). */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(CacheUnavailableException.class)
  ProblemDetail unavailable(CacheUnavailableException e) {
    return Problems.of(
        HttpStatus.SERVICE_UNAVAILABLE,
        "reference-data-unavailable",
        "Reference data unavailable",
        e.getMessage());
  }

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage());
  }
}
