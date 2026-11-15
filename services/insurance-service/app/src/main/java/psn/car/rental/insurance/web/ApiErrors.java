package psn.car.rental.insurance.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.insurance.application.ProviderUnavailableException;
import psn.car.rental.insurance.application.UnknownOptionException;
import psn.car.rental.insurance.domain.InvalidInsuranceException;
import psn.car.rental.insurance.tenant.UnknownCountryException;
import psn.car.rental.shared.Problems;

/**
 * Turns errors into Problem Details (RFC 9457, NFR-22). Request validation errors are handled by
 * the base class and also answered with Problem Details.
 */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(ProviderUnavailableException.class)
  ProblemDetail providerUnavailable(ProviderUnavailableException e) {
    return Problems.of(
        HttpStatus.SERVICE_UNAVAILABLE,
        "insurance-unavailable",
        "Insurance prices unavailable",
        "The insurance provider did not answer. Please try again later.");
  }

  @ExceptionHandler(UnknownOptionException.class)
  ProblemDetail unknownOption(UnknownOptionException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST,
        "unknown-insurance-option",
        "Unknown insurance option",
        e.getMessage());
  }

  @ExceptionHandler(InvalidInsuranceException.class)
  ProblemDetail invalid(InvalidInsuranceException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST,
        "invalid-insurance-request",
        "Invalid insurance request",
        e.getMessage());
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e.getMessage());
  }
}
