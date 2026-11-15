package psn.car.rental.ancillary.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.ancillary.application.OneWayFeeNotSetException;
import psn.car.rental.ancillary.domain.InvalidPriceException;
import psn.car.rental.ancillary.tenant.UnknownCountryException;
import psn.car.rental.shared.Problems;

/**
 * Turns errors into Problem Details (RFC 9457, NFR-22). Request validation errors are handled by
 * the base class and also answered with Problem Details.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidPriceException.class)
  ProblemDetail invalidPrice(InvalidPriceException e) {
    return Problems.of(HttpStatus.BAD_REQUEST, "invalid-price", "Invalid price", e.getMessage());
  }

  @ExceptionHandler(OneWayFeeNotSetException.class)
  ProblemDetail noFee(OneWayFeeNotSetException e) {
    return Problems.of(
        HttpStatus.NOT_FOUND, "one-way-fee-not-set", "No one-way fee", e.getMessage());
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e.getMessage());
  }
}
