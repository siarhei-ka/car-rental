package psn.car.rental.reservation.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import psn.car.rental.reservation.application.ConflictingRequestException;
import psn.car.rental.reservation.application.InventoryUnavailableException;
import psn.car.rental.reservation.application.NoCarAvailableException;
import psn.car.rental.reservation.application.PriceChangedException;
import psn.car.rental.reservation.application.PricingRefusedException;
import psn.car.rental.reservation.application.PricingUnavailableException;
import psn.car.rental.reservation.application.ReservationNotFoundException;
import psn.car.rental.reservation.domain.InvalidReservationException;
import psn.car.rental.reservation.domain.InvalidStateException;
import psn.car.rental.reservation.tenant.UnknownCountryException;
import psn.car.rental.shared.Problems;

/** Turns errors into Problem Details (NFR-22). */
@RestControllerAdvice
class ApiErrors extends ResponseEntityExceptionHandler {

  @ExceptionHandler(InvalidReservationException.class)
  ProblemDetail invalidReservation(InvalidReservationException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "invalid-reservation", "Invalid reservation", e.getMessage());
  }

  @ExceptionHandler(InvalidStateException.class)
  ProblemDetail invalidState(InvalidStateException e) {
    return Problems.of(HttpStatus.CONFLICT, "invalid-state", "Invalid state", e.getMessage());
  }

  @ExceptionHandler(NoCarAvailableException.class)
  ProblemDetail noCarAvailable(NoCarAvailableException e) {
    return Problems.of(HttpStatus.CONFLICT, "no-car-available", "No car available", e.getMessage());
  }

  @ExceptionHandler(ConflictingRequestException.class)
  ProblemDetail conflictingRequest(ConflictingRequestException e) {
    return Problems.of(
        HttpStatus.CONFLICT, "conflicting-request", "Conflicting request", e.getMessage());
  }

  @ExceptionHandler(ReservationNotFoundException.class)
  ProblemDetail notFound(ReservationNotFoundException e) {
    return Problems.of(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage());
  }

  @ExceptionHandler(InventoryUnavailableException.class)
  ProblemDetail inventoryUnavailable(InventoryUnavailableException e) {
    return Problems.of(
        HttpStatus.SERVICE_UNAVAILABLE,
        "inventory-unavailable",
        "Inventory unavailable",
        e.getMessage() + ". Please try again.");
  }

  /** The price changed: the problem carries the new price, which the customer confirms again. */
  @ExceptionHandler(PriceChangedException.class)
  ProblemDetail priceChanged(PriceChangedException e) {
    ProblemDetail problem =
        Problems.of(HttpStatus.CONFLICT, "price-changed", "The price has changed", e.getMessage());
    problem.setProperty("price", ReservationResponse.PriceResponse.of(e.price()));
    return problem;
  }

  /** A refusal of the pricing service, such as a driver who is too young, passed on unchanged. */
  @ExceptionHandler(PricingRefusedException.class)
  ProblemDetail pricingRefused(PricingRefusedException e) {
    return Problems.of(HttpStatus.valueOf(e.status()), e.type(), e.title(), e.getMessage());
  }

  @ExceptionHandler(PricingUnavailableException.class)
  ProblemDetail pricingUnavailable(PricingUnavailableException e) {
    return Problems.of(
        HttpStatus.SERVICE_UNAVAILABLE, "price-unavailable", "Price not available", e.getMessage());
  }

  @ExceptionHandler(UnknownCountryException.class)
  ProblemDetail unknownCountry(UnknownCountryException e) {
    return Problems.of(
        HttpStatus.BAD_REQUEST, "unknown-country", "Unknown country", e.getMessage());
  }
}
