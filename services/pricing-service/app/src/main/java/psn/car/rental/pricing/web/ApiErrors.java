package psn.car.rental.pricing.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.pricing.application.DependencyUnavailableException;
import psn.car.rental.pricing.application.ProfileMissingException;
import psn.car.rental.pricing.domain.InvalidPriceException;
import psn.car.rental.pricing.domain.InvalidQuoteException;
import psn.car.rental.pricing.domain.QuoteRefusedException;
import psn.car.rental.pricing.tenant.UnknownCountryException;
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

  @ExceptionHandler(InvalidQuoteException.class)
  ProblemDetail invalidQuote(InvalidQuoteException e) {
    return Problems.of(HttpStatus.BAD_REQUEST, "invalid-quote", "Invalid request", e.getMessage());
  }

  @ExceptionHandler(QuoteRefusedException.class)
  ProblemDetail refused(QuoteRefusedException e) {
    String type =
        switch (e.reason()) {
          case DRIVER_TOO_YOUNG -> "driver-too-young";
          case CAR_NOT_OFFERED -> "car-not-offered";
          case ONE_WAY_NOT_OFFERED -> "one-way-not-offered";
          case LOCATION_NOT_OFFERED -> "location-not-offered";
        };
    return Problems.of(
        HttpStatus.UNPROCESSABLE_CONTENT, type, "The rental cannot be priced", e.getMessage());
  }

  @ExceptionHandler(ProfileMissingException.class)
  ProblemDetail profileMissing(ProfileMissingException e) {
    return Problems.of(
        HttpStatus.CONFLICT, "profile-required", "Driver profile required", e.getMessage());
  }

  @ExceptionHandler(DependencyUnavailableException.class)
  ProblemDetail unavailable(DependencyUnavailableException e) {
    return Problems.of(
        HttpStatus.SERVICE_UNAVAILABLE, "price-unavailable", "Price not available", e.getMessage());
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e.getMessage());
  }
}
