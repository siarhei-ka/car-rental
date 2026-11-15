package psn.car.rental.administration.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.administration.application.ReferenceDataStore;
import psn.car.rental.administration.domain.CarClass;
import psn.car.rental.administration.domain.CarType;
import psn.car.rental.administration.domain.Country;
import psn.car.rental.administration.domain.Location;
import psn.car.rental.administration.tenant.Schemas;

/**
 * Stores reference data in PostgreSQL. Shared data lives in the {@code shared} schema, and the
 * locations of a country in the country's schema.
 */
@Repository
class JdbcReferenceDataStore implements ReferenceDataStore {

  private final JdbcClient jdbc;

  JdbcReferenceDataStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Country> countries() {
    return jdbc.sql("select code, name, currency, active from shared.country order by code")
        .query((rs, row) -> country(rs))
        .list();
  }

  @Override
  public Optional<Country> country(String code) {
    return jdbc.sql("select code, name, currency, active from shared.country where code = :code")
        .param("code", code)
        .query((rs, row) -> country(rs))
        .optional();
  }

  @Override
  public void saveCountry(Country country) {
    jdbc.sql(
            "insert into shared.country (code, name, currency, active)"
                + " values (:code, :name, :currency, :active) on conflict (code) do update set"
                + " name = excluded.name, currency = excluded.currency, active = excluded.active")
        .param("code", country.code())
        .param("name", country.name())
        .param("currency", country.currency())
        .param("active", country.active())
        .update();
  }

  @Override
  public List<CarType> carTypes() {
    return jdbc.sql("select code, name, active from shared.car_type order by code")
        .query((rs, row) ->
            new CarType(rs.getString("code"), rs.getString("name"), rs.getBoolean("active")))
        .list();
  }

  @Override
  public Optional<CarType> carType(String code) {
    return jdbc.sql("select code, name, active from shared.car_type where code = :code")
        .param("code", code)
        .query((rs, row) ->
            new CarType(rs.getString("code"), rs.getString("name"), rs.getBoolean("active")))
        .optional();
  }

  @Override
  public void saveCarType(CarType carType) {
    upsertCode("car_type", carType.code(), carType.name(), carType.active());
  }

  @Override
  public List<CarClass> carClasses() {
    return jdbc.sql("select code, name, active from shared.car_class order by code")
        .query((rs, row) ->
            new CarClass(rs.getString("code"), rs.getString("name"), rs.getBoolean("active")))
        .list();
  }

  @Override
  public Optional<CarClass> carClass(String code) {
    return jdbc.sql("select code, name, active from shared.car_class where code = :code")
        .param("code", code)
        .query((rs, row) ->
            new CarClass(rs.getString("code"), rs.getString("name"), rs.getBoolean("active")))
        .optional();
  }

  @Override
  public void saveCarClass(CarClass carClass) {
    upsertCode("car_class", carClass.code(), carClass.name(), carClass.active());
  }

  @Override
  public List<Location> locations(String country) {
    return jdbc.sql(
            "select id, name, city, latitude, longitude, time_zone, active from "
                + Schemas.ofCountry(country)
                + ".location order by id")
        .query((rs, row) -> location(rs, country))
        .list();
  }

  @Override
  public Optional<Location> location(String country, String id) {
    return jdbc.sql(
            "select id, name, city, latitude, longitude, time_zone, active from "
                + Schemas.ofCountry(country)
                + ".location where id = :id")
        .param("id", id)
        .query((rs, row) -> location(rs, country))
        .optional();
  }

  @Override
  public void saveLocation(Location location) {
    jdbc.sql(
            "insert into "
                + Schemas.ofCountry(location.country())
                + ".location (id, name, city, latitude, longitude, time_zone, active) values (:id,"
                + " :name, :city, :latitude, :longitude, :timeZone, :active) on conflict (id) do"
                + " update set name = excluded.name, city = excluded.city, latitude ="
                + " excluded.latitude, longitude = excluded.longitude, time_zone ="
                + " excluded.time_zone, active = excluded.active")
        .param("id", location.id())
        .param("name", location.name())
        .param("city", location.city())
        .param("latitude", location.latitude())
        .param("longitude", location.longitude())
        .param("timeZone", location.timeZone().getId())
        .param("active", location.active())
        .update();
  }

  /** The table name is one of two constants of this class, never input. */
  private void upsertCode(String table, String code, String name, boolean active) {
    jdbc.sql(
            "insert into shared."
                + table
                + " (code, name, active) values (:code, :name, :active) on conflict (code) do"
                + " update set name = excluded.name, active = excluded.active")
        .param("code", code)
        .param("name", name)
        .param("active", active)
        .update();
  }

  private static Country country(ResultSet rs) throws SQLException {
    return new Country(
        rs.getString("code"),
        rs.getString("name"),
        rs.getString("currency"),
        rs.getBoolean("active"));
  }

  private static Location location(ResultSet rs, String country) throws SQLException {
    return new Location(
        rs.getString("id"),
        country,
        rs.getString("name"),
        rs.getString("city"),
        rs.getDouble("latitude"),
        rs.getDouble("longitude"),
        ZoneId.of(rs.getString("time_zone")),
        rs.getBoolean("active"));
  }
}
