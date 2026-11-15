package psn.car.rental.discount.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.discount.application.RuleNotFoundException;
import psn.car.rental.discount.domain.InvalidDiscountException;
import psn.car.rental.discount.tenant.UnknownCountryException;
import psn.car.rental.shared.Problems;

/**
 * Turns errors into Problem Details (RFC 9457, NFR-22). Request validation errors are handled by
 * the base class and also answered with Problem Details.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidDiscountException.class)
  ProblemDetail invalid(InvalidDiscountException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "invalid-discount", "Invalid discount request", e.getMessage());
  }

  @ExceptionHandler(RuleNotFoundException.class)
  ProblemDetail notFound(RuleNotFoundException e) {
    return Problems.of(
        HttpStatus.NOT_FOUND, "discount-rule-not-found", "No such discount rule", e.getMessage());
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e.getMessage());
  }
}
