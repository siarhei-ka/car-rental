package psn.car.rental.inventory.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.inventory.application.InventoryService;
import psn.car.rental.inventory.application.LedgerStore;
import psn.car.rental.inventory.application.PlacedTransfer;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.domain.StockLedger;
import psn.car.rental.inventory.domain.TransferId;
import psn.car.rental.inventory.tenant.Countries;
import psn.car.rental.inventory.web.Dtos.StockRequest;
import psn.car.rental.inventory.web.Dtos.StockResponse;
import psn.car.rental.inventory.web.Dtos.TransferRequest;
import psn.car.rental.inventory.web.Dtos.TransferResponse;

/**
 * Administration of stock and transfers. The API gateway will restrict these routes to
 * administrators of the country (build step 4).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController {

  private final InventoryService inventory;
  private final Countries countries;

  AdminController(InventoryService inventory, Countries countries) {
    this.inventory = inventory;
    this.countries = countries;
  }

  @GetMapping("/stock")
  List<StockResponse> listStock(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam(required = false) @Pattern(regexp = Dtos.ID) String locationId) {
    return inventory
        .listStock(countries.require(country), Optional.ofNullable(locationId).map(LocationId::new))
        .stream()
        .map(StockResponse::of)
        .toList();
  }

  @PutMapping("/stock/{locationId}/{carType}/{carClass}")
  StockResponse changeStock(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String locationId,
      @PathVariable @Pattern(regexp = Dtos.CODE) String carType,
      @PathVariable @Pattern(regexp = Dtos.CODE) String carClass,
      @Valid @RequestBody StockRequest request) {
    StockKey key = Dtos.key(locationId, carType, carClass);
    StockLedger ledger = inventory.changeStock(countries.require(country), key, request.cars());
    return StockResponse.of(new LedgerStore.StockRow(key, ledger.stock()));
  }

  /** Transfers whose arrival has not been registered yet. */
  @GetMapping("/transfers")
  List<TransferResponse> listTransfers(
      @RequestHeader(name = "X-Country", required = false) String country,
      @RequestParam(required = false) @Pattern(regexp = Dtos.ID) String locationId) {
    return inventory
        .listTransfers(
            countries.require(country), Optional.ofNullable(locationId).map(LocationId::new))
        .stream()
        .map(pending -> TransferResponse.of(pending.key(), pending.transfer()))
        .toList();
  }

  @PutMapping("/transfers/{transferId}")
  ResponseEntity<TransferResponse> dispatchTransfer(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String transferId,
      @Valid @RequestBody TransferRequest request) {
    PlacedTransfer placed =
        inventory.dispatchTransfer(
            countries.require(country),
            Dtos.key(request.locationId(), request.carType(), request.carClass()),
            new TransferId(transferId),
            request.cars(),
            request.departure(),
            new LocationId(request.targetLocationId()),
            request.plannedArrival());
    return ResponseEntity.status(placed.created() ? HttpStatus.CREATED : HttpStatus.OK)
        .body(TransferResponse.of(placed.key(), placed.transfer()));
  }

  /** Registers that the cars of a transfer arrived at the target location. */
  @PostMapping("/transfers/{transferId}/arrival")
  ResponseEntity<Void> registerArrival(
      @RequestHeader(name = "X-Country", required = false) String country,
      @PathVariable @Pattern(regexp = Dtos.ID) String transferId) {
    inventory.registerArrival(countries.require(country), new TransferId(transferId));
    return ResponseEntity.noContent().build();
  }
}
