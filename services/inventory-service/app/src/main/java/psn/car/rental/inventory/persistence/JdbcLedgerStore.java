package psn.car.rental.inventory.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.inventory.application.LedgerStore;
import psn.car.rental.inventory.domain.CarClassCode;
import psn.car.rental.inventory.domain.CarTypeCode;
import psn.car.rental.inventory.domain.Hold;
import psn.car.rental.inventory.domain.HoldId;
import psn.car.rental.inventory.domain.HoldStatus;
import psn.car.rental.inventory.domain.LocationId;
import psn.car.rental.inventory.domain.RentalPeriod;
import psn.car.rental.inventory.domain.StockKey;
import psn.car.rental.inventory.domain.StockLedger;
import psn.car.rental.inventory.domain.Transfer;
import psn.car.rental.inventory.domain.TransferId;
import psn.car.rental.inventory.tenant.Country;

/** Stores stock ledgers in PostgreSQL with explicit SQL, so that the row lock is visible. */
@Repository
class JdbcLedgerStore implements LedgerStore {

  private static final String KEY_MATCH =
      "location_id = :location and car_type = :carType and car_class = :carClass";

  private final JdbcClient jdbc;

  JdbcLedgerStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void useTenant(Country country) {
    // The third argument makes the setting local to the current transaction.
    jdbc.sql("select set_config('search_path', :schema, true)")
        .param("schema", country.schema())
        .query(String.class)
        .single();
  }

  @Override
  public void ensureStock(StockKey key) {
    keyParams(
            jdbc.sql(
                "insert into stock (location_id, car_type, car_class, cars)"
                    + " values (:location, :carType, :carClass, 0) on conflict do nothing"),
            key)
        .update();
  }

  @Override
  public Optional<Integer> lockStock(StockKey key) {
    return keyParams(jdbc.sql("select cars from stock where " + KEY_MATCH + " for update"), key)
        .query(Integer.class)
        .optional();
  }

  @Override
  public StockLedger load(StockKey key, int stock) {
    List<Hold> holds =
        keyParams(
                jdbc.sql(
                    "select id, start_at, days, return_location_id, one_way, status, created_at"
                        + " from hold where "
                        + KEY_MATCH
                        + " order by created_at, id"),
                key)
            .query((rs, row) -> hold(rs))
            .list();
    List<Transfer> transfers =
        keyParams(
                jdbc.sql(
                    "select id, cars, departure_at, target_location_id, planned_arrival_at"
                        + " from transfer where "
                        + KEY_MATCH
                        + " order by departure_at, id"),
                key)
            .query((rs, row) -> transfer(rs))
            .list();
    return StockLedger.restore(key, stock, holds, transfers);
  }

  @Override
  public List<StockRow> listStock(Optional<LocationId> location) {
    String sql =
        "select location_id, car_type, car_class, cars from stock"
            + (location.isPresent() ? " where location_id = :location" : "")
            + " order by location_id, car_type, car_class";
    JdbcClient.StatementSpec statement = jdbc.sql(sql);
    if (location.isPresent()) {
      statement = statement.param("location", location.get().value());
    }
    return statement.query((rs, row) -> new StockRow(stockKey(rs), rs.getInt("cars"))).list();
  }

  @Override
  public Optional<HoldRef> findHold(HoldId id) {
    return jdbc.sql(
            "select location_id, car_type, car_class, return_location_id from hold where id = :id")
        .param("id", id.value())
        .query((rs, row) ->
            new HoldRef(stockKey(rs), new LocationId(rs.getString("return_location_id"))))
        .optional();
  }

  @Override
  public void insertHold(StockKey key, Hold hold) {
    keyParams(
            jdbc.sql(
                "insert into hold (id, location_id, car_type, car_class, start_at, days,"
                    + " return_location_id, one_way, status, created_at) values (:id, :location,"
                    + " :carType, :carClass, :start, :days, :returnLocation, :oneWay, :status,"
                    + " :createdAt)"),
            key)
        .param("id", hold.id().value())
        .param("start", timestamp(hold.period().start()))
        .param("days", hold.period().days())
        .param("returnLocation", hold.returnLocation().value())
        .param("oneWay", hold.oneWay())
        .param("status", hold.status().name())
        .param("createdAt", timestamp(hold.createdAt()))
        .update();
  }

  @Override
  public void updateHoldStatus(HoldId id, HoldStatus status) {
    jdbc.sql("update hold set status = :status where id = :id")
        .param("status", status.name())
        .param("id", id.value())
        .update();
  }

  @Override
  public void deleteHolds(List<HoldId> ids) {
    if (ids.isEmpty()) {
      return;
    }
    jdbc.sql("delete from hold where id in (:ids)")
        .param("ids", ids.stream().map(HoldId::value).toList())
        .update();
  }

  @Override
  public Optional<TransferRef> findTransfer(TransferId id) {
    return jdbc.sql(
            "select location_id, car_type, car_class, target_location_id from transfer"
                + " where id = :id")
        .param("id", id.value())
        .query((rs, row) ->
            new TransferRef(stockKey(rs), new LocationId(rs.getString("target_location_id"))))
        .optional();
  }

  @Override
  public void insertTransfer(StockKey key, Transfer transfer) {
    keyParams(
            jdbc.sql(
                "insert into transfer (id, location_id, car_type, car_class, cars, departure_at,"
                    + " target_location_id, planned_arrival_at) values (:id, :location, :carType,"
                    + " :carClass, :cars, :departure, :target, :plannedArrival)"),
            key)
        .param("id", transfer.id().value())
        .param("cars", transfer.cars())
        .param("departure", timestamp(transfer.departure()))
        .param("target", transfer.target().value())
        .param("plannedArrival", timestamp(transfer.plannedArrival()))
        .update();
  }

  @Override
  public void deleteTransfer(TransferId id) {
    jdbc.sql("delete from transfer where id = :id").param("id", id.value()).update();
  }

  @Override
  public List<PendingTransfer> listTransfers(Optional<LocationId> from) {
    String sql =
        "select location_id, car_type, car_class, id, cars, departure_at, target_location_id,"
            + " planned_arrival_at from transfer"
            + (from.isPresent() ? " where location_id = :location" : "")
            + " order by departure_at, id";
    JdbcClient.StatementSpec statement = jdbc.sql(sql);
    if (from.isPresent()) {
      statement = statement.param("location", from.get().value());
    }
    return statement.query((rs, row) -> new PendingTransfer(stockKey(rs), transfer(rs))).list();
  }

  @Override
  public void updateStock(StockKey key, int cars) {
    keyParams(jdbc.sql("update stock set cars = :cars where " + KEY_MATCH), key)
        .param("cars", cars)
        .update();
  }

  @Override
  public List<StockKey> keysWithProvisionalHoldsBefore(Instant cutoff) {
    return jdbc.sql(
            "select distinct location_id, car_type, car_class from hold"
                + " where status = 'PROVISIONAL' and created_at <= :cutoff")
        .param("cutoff", timestamp(cutoff))
        .query((rs, row) -> stockKey(rs))
        .list();
  }

  private static JdbcClient.StatementSpec keyParams(JdbcClient.StatementSpec spec, StockKey key) {
    return spec.param("location", key.location().value())
        .param("carType", key.carType().value())
        .param("carClass", key.carClass().value());
  }

  private static StockKey stockKey(ResultSet rs) throws SQLException {
    return new StockKey(
        new LocationId(rs.getString("location_id")),
        new CarTypeCode(rs.getString("car_type")),
        new CarClassCode(rs.getString("car_class")));
  }

  private static Hold hold(ResultSet rs) throws SQLException {
    return new Hold(
        new HoldId(rs.getString("id")),
        new RentalPeriod(instant(rs, "start_at"), rs.getInt("days")),
        new LocationId(rs.getString("return_location_id")),
        rs.getBoolean("one_way"),
        HoldStatus.valueOf(rs.getString("status")),
        instant(rs, "created_at"));
  }

  private static Transfer transfer(ResultSet rs) throws SQLException {
    return new Transfer(
        new TransferId(rs.getString("id")),
        rs.getInt("cars"),
        instant(rs, "departure_at"),
        new LocationId(rs.getString("target_location_id")),
        instant(rs, "planned_arrival_at"));
  }

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    return rs.getObject(column, OffsetDateTime.class).toInstant();
  }
}
