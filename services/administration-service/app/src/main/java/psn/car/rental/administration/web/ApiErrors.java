package psn.car.rental.administration.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.administration.application.NotFoundException;
import psn.car.rental.administration.application.UnknownCountryException;
import psn.car.rental.administration.domain.InvalidReferenceDataException;
import psn.car.rental.shared.Problems;

/** Turns errors into Problem Details (NFR-22). */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidReferenceDataException.class)
  ProblemDetail invalid(InvalidReferenceDataException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "invalid-reference-data", "Invalid reference data", e.getMessage());
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e.getMessage());
  }

  @ExceptionHandler(NotFoundException.class)
  ProblemDetail notFound(NotFoundException e) {
    return Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage());
  }
}
