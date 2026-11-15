package psn.car.rental.reservation.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.reservation.application.ReservationService;
import psn.car.rental.reservation.application.ReservationService.CreatedReservation;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Countries;

/**
 * Reservations, for customers. The customer is the subject of the access token: a customer reads
 * and cancels only their own reservations.
 */
@RestController
@RequestMapping("/api/v1/reservations")
class ReservationController {

  private final ReservationService reservations;
  private final Countries countries;

  ReservationController(ReservationService reservations, Countries countries) {
    this.reservations = reservations;
    this.countries = countries;
  }

  /** Creates a reservation. A request repeated with the same idempotency key creates nothing. */
  @PostMapping
  ResponseEntity<ReservationResponse> create(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestHeader("Idempotency-Key") @Pattern(regexp = Patterns.IDEMPOTENCY_KEY)
          String idempotencyKey,
      @Valid @RequestBody ReservationRequest request,
      @AuthenticationPrincipal Jwt token) {
    var tenant = countries.requireActive(country);
    CreatedReservation result =
        reservations.create(
            tenant,
            token.getSubject(),
            idempotencyKey,
            request.toRental(),
            request.expectedTotal().currency(),
            request.expectedTotal().value());
    ReservationResponse body = ReservationResponse.of(tenant.code(), result.reservation());
    if (!result.created()) {
      return ResponseEntity.ok(body);
    }
    return ResponseEntity.created(URI.create("/api/v1/reservations/" + body.id())).body(body);
  }

  /** The customer's own reservations in every country, latest start first. */
  @GetMapping
  List<ReservationResponse> listOwn(@AuthenticationPrincipal Jwt token) {
    return reservations.listOwn(token.getSubject()).stream()
        .map(own -> ReservationResponse.of(own.country().code(), own.reservation()))
        .toList();
  }

  @GetMapping("/{id}")
  ReservationResponse get(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Patterns.UUID) String id,
      @AuthenticationPrincipal Jwt token) {
    var tenant = countries.require(country);
    return ReservationResponse.of(
        tenant.code(), reservations.getOwn(tenant, reservationId(id), token.getSubject()));
  }

  @PostMapping("/{id}/cancel")
  ReservationResponse cancel(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Patterns.UUID) String id,
      @AuthenticationPrincipal Jwt token) {
    var tenant = countries.require(country);
    return ReservationResponse.of(
        tenant.code(), reservations.cancelOwn(tenant, reservationId(id), token.getSubject()));
  }

  static ReservationId reservationId(String id) {
    return new ReservationId(UUID.fromString(id));
  }
}
