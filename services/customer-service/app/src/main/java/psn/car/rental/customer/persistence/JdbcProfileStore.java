package psn.car.rental.customer.persistence;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import psn.car.rental.customer.application.ProfileStore;
import psn.car.rental.customer.domain.CustomerId;
import psn.car.rental.customer.domain.DriverProfile;
import psn.car.rental.customer.domain.LicenceCountry;

/** Driver profiles in PostgreSQL, one row per customer. */
@Repository
class JdbcProfileStore implements ProfileStore {

  private final JdbcClient jdbc;
  private final Clock clock;

  JdbcProfileStore(JdbcClient jdbc, Clock clock) {
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Override
  public Optional<DriverProfile> find(CustomerId customer) {
    return jdbc.sql(
            """
            select date_of_birth, licence_country from driver_profile where customer_id = :customer
            """)
        .param("customer", customer.value())
        .query((row, n) ->
            new DriverProfile(
                customer,
                row.getObject("date_of_birth", java.time.LocalDate.class),
                new LicenceCountry(row.getString("licence_country"))))
        .optional();
  }

  @Override
  public void save(DriverProfile profile) {
    jdbc.sql(
            """
            insert into driver_profile
              (customer_id, date_of_birth, licence_country, created_at, updated_at)
            values (:customer, :dateOfBirth, :country, :now, :now)
            on conflict (customer_id) do update
              set date_of_birth = excluded.date_of_birth,
                  licence_country = excluded.licence_country,
                  updated_at = excluded.updated_at
            """)
        .param("customer", profile.customer().value())
        .param("dateOfBirth", profile.dateOfBirth())
        .param("country", profile.licenceCountry().code())
        .param("now", OffsetDateTime.now(clock))
        .update();
  }
}
