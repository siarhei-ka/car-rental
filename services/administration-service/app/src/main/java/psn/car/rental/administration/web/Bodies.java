package psn.car.rental.administration.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import psn.car.rental.administration.domain.Location;

/** Request and response bodies of the administration API. They match {@code openapi.yaml}. */
final class Bodies {

  static final String COUNTRY = "[a-z]{2}";
  static final String ID = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}";
  static final String CODE = "[A-Z][A-Z0-9_]{0,31}";

  private Bodies() {}

  record CountryRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(min = 3, max = 3) String currency,
      @NotNull Boolean active) {}

  record NamedRequest(@NotBlank @Size(max = 100) String name, @NotNull Boolean active) {}

  record LocationRequest(
      @NotBlank @Size(max = 100) String name,
      @NotBlank @Size(max = 100) String city,
      @NotNull Double latitude,
      @NotNull Double longitude,
      @NotBlank @Size(max = 64) String timeZone,
      @NotNull Boolean active) {}

  record LocationResponse(
      String id,
      String country,
      String name,
      String city,
      double latitude,
      double longitude,
      String timeZone,
      boolean active) {

    static LocationResponse of(Location location) {
      return new LocationResponse(
          location.id(),
          location.country(),
          location.name(),
          location.city(),
          location.latitude(),
          location.longitude(),
          location.timeZone().getId(),
          location.active());
    }
  }
}
