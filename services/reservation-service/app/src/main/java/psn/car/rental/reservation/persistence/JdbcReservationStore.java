package psn.car.rental.reservation.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.reservation.application.ReservationStore;
import psn.car.rental.reservation.domain.ExtraChoice;
import psn.car.rental.reservation.domain.InventoryAction;
import psn.car.rental.reservation.domain.Price;
import psn.car.rental.reservation.domain.RentalRequest;
import psn.car.rental.reservation.domain.Reservation;
import psn.car.rental.reservation.domain.ReservationId;
import psn.car.rental.reservation.domain.ReservationState;
import psn.car.rental.reservation.tenant.Country;
import tools.jackson.databind.json.JsonMapper;

/** Stores reservations in PostgreSQL with explicit SQL. */
@Repository
class JdbcReservationStore implements ReservationStore {

  private static final String COLUMNS =
      "r.id, r.customer_id, r.pick_up_location_id, r.return_location_id, r.car_type, r.car_class, r.start_at,"
          + " r.days, r.insurance, r.extras, r.price, r.state, r.pending_action, r.created_at";

  private final JdbcClient jdbc;
  private final JsonMapper json;

  JdbcReservationStore(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
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
  public boolean insert(Reservation reservation, String idempotencyKey, String requestHash) {
    RentalRequest rental = reservation.rental();
    Instant now = reservation.createdAt();
    int inserted =
        jdbc.sql(
                "insert into reservation (id, customer_id, pick_up_location_id, return_location_id, car_type,"
                    + " car_class, start_at, days, insurance, extras, price, total, currency, state,"
                    + " pending_action, pending_since, created_at, updated_at) values (:id,"
                    + " :customer, :pickUp, :returnTo, :carType, :carClass, :start, :days,"
                    + " :insurance, :extras, cast(:price as jsonb), :total, :currency, :state,"
                    + " :pending, :pendingSince, :now, :now) on conflict (id) do nothing")
            .param("id", reservation.id().value())
            .param("customer", reservation.customerId())
            .param("pickUp", rental.pickUpLocationId())
            .param("returnTo", rental.returnLocationId())
            .param("carType", rental.carType())
            .param("carClass", rental.carClass())
            .param("start", timestamp(rental.start()))
            .param("days", rental.days())
            .param("insurance", rental.insurance())
            .param(
                "extras",
                rental.extras().stream()
                    .map(e -> e.code() + ":" + e.quantity())
                    .collect(Collectors.joining(",")))
            .param("price", reservation.price().map(this::write).orElse(null))
            .param("total", reservation.price().map(Price::total).orElse(null))
            .param("currency", reservation.price().map(Price::currency).orElse(null))
            .param("state", reservation.state().name())
            .param("pending", reservation.pendingAction().map(Enum::name).orElse(null))
            .param(
                "pendingSince", reservation.pendingAction().map(a -> timestamp(now)).orElse(null))
            .param("now", timestamp(now))
            .update();
    if (inserted == 0) {
      return false;
    }
    jdbc.sql(
            "insert into idempotency_key (customer_id, key, request_hash, reservation_id,"
                + " created_at) values (:customer, :key, :hash, :id, :now)")
        .param("customer", reservation.customerId())
        .param("key", idempotencyKey)
        .param("hash", requestHash)
        .param("id", reservation.id().value())
        .param("now", timestamp(now))
        .update();
    return true;
  }

  @Override
  public Optional<StoredRequest> findByIdempotencyKey(String customerId, String idempotencyKey) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + ", k.request_hash from idempotency_key k"
                + " join reservation r on r.id = k.reservation_id"
                + " where k.customer_id = :customer and k.key = :key")
        .param("customer", customerId)
        .param("key", idempotencyKey)
        .query((rs, row) -> new StoredRequest(reservation(rs), rs.getString("request_hash")))
        .optional();
  }

  @Override
  public Optional<Reservation> find(ReservationId id) {
    return jdbc.sql("select " + COLUMNS + " from reservation r where r.id = :id")
        .param("id", id.value())
        .query((rs, row) -> reservation(rs))
        .optional();
  }

  @Override
  public Optional<Reservation> lock(ReservationId id) {
    return jdbc.sql("select " + COLUMNS + " from reservation r where r.id = :id for update")
        .param("id", id.value())
        .query((rs, row) -> reservation(rs))
        .optional();
  }

  @Override
  public void update(Reservation reservation, Instant now) {
    String pending = reservation.pendingAction().map(Enum::name).orElse(null);
    jdbc.sql(
            "update reservation set state = :state, updated_at = :now,"
                + " pending_since = case when :pending::varchar is null then null"
                + " when pending_action is not distinct from :pending::varchar then pending_since"
                + " else :now end,"
                + " pending_action = :pending::varchar where id = :id")
        .param("state", reservation.state().name())
        .param("pending", pending)
        .param("now", timestamp(now))
        .param("id", reservation.id().value())
        .update();
  }

  @Override
  public void delete(ReservationId id) {
    jdbc.sql("delete from reservation where id = :id").param("id", id.value()).update();
  }

  @Override
  public List<ReservationId> withPendingActionSince(Instant cutoff) {
    return jdbc.sql(
            "select id from reservation where pending_action is not null"
                + " and pending_since <= :cutoff order by pending_since")
        .param("cutoff", timestamp(cutoff))
        .query((rs, row) -> new ReservationId(rs.getObject("id", UUID.class)))
        .list();
  }

  @Override
  public List<Reservation> listByCustomer(String customerId) {
    return jdbc.sql(
            "select "
                + COLUMNS
                + " from reservation r where r.customer_id = :customer order by r.start_at, r.id")
        .param("customer", customerId)
        .query((rs, row) -> reservation(rs))
        .list();
  }

  @Override
  public List<Reservation> list(Optional<String> pickUpLocationId) {
    String sql =
        "select "
            + COLUMNS
            + " from reservation r"
            + (pickUpLocationId.isPresent() ? " where r.pick_up_location_id = :location" : "")
            + " order by r.start_at, r.id";
    JdbcClient.StatementSpec statement = jdbc.sql(sql);
    if (pickUpLocationId.isPresent()) {
      statement = statement.param("location", pickUpLocationId.get());
    }
    return statement.query((rs, row) -> reservation(rs)).list();
  }

  private Reservation reservation(ResultSet rs) throws SQLException {
    String pending = rs.getString("pending_action");
    String price = rs.getString("price");
    return new Reservation(
        new ReservationId(rs.getObject("id", UUID.class)),
        rs.getString("customer_id"),
        new RentalRequest(
            rs.getString("pick_up_location_id"),
            rs.getString("return_location_id"),
            rs.getString("car_type"),
            rs.getString("car_class"),
            rs.getObject("start_at", OffsetDateTime.class).toInstant(),
            rs.getInt("days"),
            rs.getString("insurance"),
            extras(rs.getString("extras"))),
        Optional.ofNullable(price).map(this::read),
        ReservationState.valueOf(rs.getString("state")),
        Optional.ofNullable(pending).map(InventoryAction::valueOf),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }

  private static List<ExtraChoice> extras(String text) {
    if (text.isEmpty()) {
      return List.of();
    }
    return Arrays.stream(text.split(","))
        .map(part -> part.split(":"))
        .map(part -> new ExtraChoice(part[0], Integer.parseInt(part[1])))
        .toList();
  }

  private String write(Price price) {
    return json.writeValueAsString(
        new PriceJson(
            price.currency(),
            price.lines(),
            price.subtotal(),
            price.discount().orElse(null),
            price.total()));
  }

  private Price read(String text) {
    PriceJson stored = json.readValue(text, PriceJson.class);
    return new Price(
        stored.currency(),
        stored.lines(),
        stored.subtotal(),
        Optional.ofNullable(stored.discount()),
        stored.total());
  }

  /** A price as it is stored in the {@code price} column. */
  record PriceJson(
      String currency,
      List<Price.Line> lines,
      BigDecimal subtotal,
      Price.Discount discount,
      BigDecimal total) {}

  private static OffsetDateTime timestamp(Instant instant) {
    return instant.atOffset(ZoneOffset.UTC);
  }
}
