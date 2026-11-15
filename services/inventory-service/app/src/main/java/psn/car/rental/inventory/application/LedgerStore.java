package psn.car.rental.inventory.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import psn.car.rental.inventory.domain.Hold;
import psn.car.rental.inventory.domain.HoldId;
import psn.car.rental.inventory.domain.HoldStatus;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.domain.StockLedger;
import psn.car.rental.inventory.domain.Transfer;
import psn.car.rental.inventory.domain.TransferId;
import psn.car.rental.inventory.tenant.Country;

/**
 * Storage of stock ledgers. Every method runs inside the caller's transaction, after {@link
 * #useTenant(Country)} has selected the country schema.
 */
public interface LedgerStore {

  /** Directs the following statements of the current transaction to the country's schema. */
  void useTenant(Country country);

  /** Creates the stock row with 0 cars if it does not exist yet. */
  void ensureStock(StockKey key);

  /** Locks the stock row until the transaction ends and returns the number of cars. */
  Optional<Integer> lockStock(StockKey key);

  /** Loads the holds and transfers of a stock row that is already locked or only read. */
  StockLedger load(StockKey key, int stock);

  /** The stock rows of a location, or of all locations when {@code location} is empty. */
  List<StockRow> listStock(Optional<LocationId> location);

  Optional<HoldRef> findHold(HoldId id);

  void insertHold(StockKey key, Hold hold);

  void updateHoldStatus(HoldId id, HoldStatus status);

  void deleteHolds(List<HoldId> ids);

  Optional<TransferRef> findTransfer(TransferId id);

  void insertTransfer(StockKey key, Transfer transfer);

  void deleteTransfer(TransferId id);

  /** Transfers that have not arrived, from one location or from all, ordered by departure. */
  List<PendingTransfer> listTransfers(Optional<LocationId> from);

  void updateStock(StockKey key, int cars);

  /** Stock rows that have provisional holds created at or before the cutoff. */
  List<StockKey> keysWithProvisionalHoldsBefore(Instant cutoff);

  /** Where a hold is stored, and where its car is returned. */
  record HoldRef(StockKey key, LocationId returnLocation) {}

  /** Where a transfer is stored, and where its cars go. */
  record TransferRef(StockKey key, LocationId target) {}

  /** A transfer together with its source stock row. */
  record PendingTransfer(StockKey key, Transfer transfer) {}

  /** One stock row. */
  record StockRow(StockKey key, int cars) {}
}
