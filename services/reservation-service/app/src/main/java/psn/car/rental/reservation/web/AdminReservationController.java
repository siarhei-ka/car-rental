package psn.car.rental.reservation.web;

import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.reservation.application.ReservationService;
import psn.car.rental.reservation.tenant.Countries;

/**
 * The reservations of a country, and their pick-ups, returns and no-shows, registered by staff at a
 * location. Only administrators of the country reach them.
 */
@RestController
@RequestMapping("/api/v1/admin/reservations")
class AdminReservationController {

  private final ReservationService reservations;
  private final Countries countries;

  AdminReservationController(ReservationService reservations, Countries countries) {
    this.reservations = reservations;
    this.countries = countries;
  }

  @GetMapping
  List<ReservationResponse> list(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam(required = false) @Pattern(regexp = Patterns.ID) String locationId) {
    var tenant = countries.require(country);
    return reservations.list(tenant, Optional.ofNullable(locationId)).stream()
        .map(reservation -> ReservationResponse.of(tenant.code(), reservation))
        .toList();
  }

  @PostMapping("/{id}/pick-up")
  ReservationResponse pickUp(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Patterns.UUID) String id) {
    var tenant = countries.require(country);
    return ReservationResponse.of(
        tenant.code(), reservations.pickUp(tenant, ReservationController.reservationId(id)));
  }

  @PostMapping("/{id}/return")
  ReservationResponse registerReturn(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Patterns.UUID) String id) {
    var tenant = countries.require(country);
    return ReservationResponse.of(
        tenant.code(),
        reservations.registerReturn(tenant, ReservationController.reservationId(id)));
  }

  @PostMapping("/{id}/no-show")
  ReservationResponse markNoShow(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Patterns.UUID) String id) {
    var tenant = countries.require(country);
    return ReservationResponse.of(
        tenant.code(), reservations.markNoShow(tenant, ReservationController.reservationId(id)));
  }
}
