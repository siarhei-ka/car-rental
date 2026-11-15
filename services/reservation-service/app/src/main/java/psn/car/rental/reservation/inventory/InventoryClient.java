package psn.car.rental.reservation.inventory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import psn.car.rental.reservation.application.ConflictingRequestException;
import psn.car.rental.reservation.application.HoldNotFoundException;
import psn.car.rental.reservation.application.Inventory;
import psn.car.rental.reservation.application.InventoryUnavailableException;
import psn.car.rental.reservation.application.NoCarAvailableException;
import psn.car.rental.reservation.domain.InvalidReservationException;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.tenant.Country;

/**
 * Calls the inventory service over HTTP and turns its answers into the errors of {@link Inventory}.
 */
public class InventoryClient implements Inventory {

  private final RestClient http;

  public InventoryClient(RestClient http) {
    this.http = http;
  }

  @Override
  public void placeHold(Country country, ReservationId id, RentalRequest rental) {
    call(() ->
        http.put()
            .uri("/api/v1/holds/{id}", id.value())
            .header("X-Country", country.code())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "locationId", rental.pickUpLocationId(),
                    "carType", rental.carType(),
                    "carClass", rental.carClass(),
                    "start", rental.start().toString(),
                    "days", rental.days(),
                    "returnLocationId", rental.returnLocationId()))
            .retrieve()
            .onStatus(status -> status.isError(), (request, response) -> fail(response))
            .toBodilessEntity());
  }

  @Override
  public void confirmHold(Country country, ReservationId id) {
    post(country, "/api/v1/holds/{id}/confirm", id);
  }

  @Override
  public void releaseHold(Country country, ReservationId id) {
    call(() ->
        http.delete()
            .uri("/api/v1/holds/{id}", id.value())
            .header("X-Country", country.code())
            .retrieve()
            .onStatus(status -> status.isError(), (request, response) -> fail(response))
            .toBodilessEntity());
  }

  @Override
  public void registerReturn(Country country, ReservationId id) {
    post(country, "/api/v1/holds/{id}/return", id);
  }

  private void post(Country country, String path, ReservationId id) {
    call(() ->
        http.post()
            .uri(path, id.value())
            .header("X-Country", country.code())
            .retrieve()
            .onStatus(status -> status.isError(), (request, response) -> fail(response))
            .toBodilessEntity());
  }

  private static void call(Runnable request) {
    try {
      request.run();
    } catch (ResourceAccessException e) {
      throw new InventoryUnavailableException("The inventory service did not answer in time", e);
    } catch (RestClientException e) {
      throw new InventoryUnavailableException("The inventory service failed", e);
    }
  }

  private static void fail(ClientHttpResponse response) throws IOException {
    HttpStatus status = HttpStatus.resolve(response.getStatusCode().value());
    String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
    if (status == HttpStatus.CONFLICT && body.contains("/problems/no-car-available")) {
      throw new NoCarAvailableException("No car is free for the requested rental");
    }
    if (status == HttpStatus.CONFLICT && body.contains("/problems/conflicting-request")) {
      throw new ConflictingRequestException("The hold already exists for a different rental");
    }
    if (status == HttpStatus.NOT_FOUND) {
      throw new HoldNotFoundException("The inventory has no hold for this reservation");
    }
    if (status == HttpStatus.BAD_REQUEST) {
      throw new InvalidReservationException("The inventory rejected the request: " + body);
    }
    throw new InventoryUnavailableException(
        "The inventory service answered " + response.getStatusCode().value());
  }
}
