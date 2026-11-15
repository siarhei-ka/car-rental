package psn.car.rental.inventory.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.inventory.domain.ConflictingRequestException;
import psn.car.rental.inventory.domain.HoldNotFoundException;
import psn.car.rental.inventory.domain.NoCarAvailableException;
import psn.car.rental.inventory.domain.StockInUseException;
import psn.car.rental.inventory.domain.TransferNotFoundException;
import psn.car.rental.inventory.tenant.UnknownCountryException;
import psn.car.rental.shared.Problems;

/**
 * Turns errors into Problem Details (RFC 9457, NFR-22). Request validation errors are handled by
 * the base class and also answered with Problem Details.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(NoCarAvailableException.class)
  ProblemDetail noCarAvailable(NoCarAvailableException e) {
    return problem(HttpStatus.CONFLICT, "no-car-available", "No car available", e);
  }

  @ExceptionHandler(StockInUseException.class)
  ProblemDetail stockInUse(StockInUseException e) {
    return problem(HttpStatus.CONFLICT, "stock-in-use", "Stock in use", e);
  }

  @ExceptionHandler(ConflictingRequestException.class)
  ProblemDetail conflictingRequest(ConflictingRequestException e) {
    return problem(HttpStatus.CONFLICT, "conflicting-request", "Conflicting request", e);
  }

  @ExceptionHandler({HoldNotFoundException.class, TransferNotFoundException.class})
  ProblemDetail notFound(RuntimeException e) {
    return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", e);
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return problem(HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalidRequest(IllegalArgumentException e) {
    return problem(HttpStatus.BAD_REQUEST, "invalid-request", "Invalid request", e);
  }

  private static ProblemDetail problem(
      HttpStatus status, String type, String title, RuntimeException e) {
    return Problems.of(status, type, title, e.getMessage());
  }
}
