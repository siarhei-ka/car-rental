package psn.car.rental.customer.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.customer.application.ProfileNotFoundException;
import psn.car.rental.customer.domain.InvalidProfileException;
import psn.car.rental.shared.Problems;

/** Turns errors into Problem Details (NFR-22). */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidProfileException.class)
  ProblemDetail invalid(InvalidProfileException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "invalid-profile", "Invalid driver profile", e.getMessage());
  }

  @ExceptionHandler(ProfileNotFoundException.class)
  ProblemDetail notFound(ProfileNotFoundException e) {
    return Problems.of(
        HttpStatus.NOT_FOUND, "profile-not-found", "No driver profile", e.getMessage());
  }
}
