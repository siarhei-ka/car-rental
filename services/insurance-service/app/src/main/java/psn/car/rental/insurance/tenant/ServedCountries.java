package psn.car.rental.insurance.tenant;

import java.util.Currency;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import psn.car.rental.insurance.config.InsuranceProperties;

/**
 * Remembers each served country and its currency in the table {@code served_country}, so that
 * insurance prices keep their currency after a restart. Insurance options are shared by all
 * countries, so a country needs no schema of its own.
 */
@Component
public class ServedCountries implements InitializingBean {

  private final JdbcClient jdbc;
  private final Countries countries;
  private final InsuranceProperties properties;

  public ServedCountries(JdbcClient jdbc, Countries countries, InsuranceProperties properties) {
    this.jdbc = jdbc;
    this.countries = countries;
    this.properties = properties;
  }

  @Override
  public void afterPropertiesSet() {
    jdbc.sql("select code, active, currency from served_country order by code")
        .query((row, n) -> {
          countries.register(
              new Country(row.getString("code")),
              row.getBoolean("active"),
              row.getString("currency"));
          return null;
        })
        .list();
    properties
        .countries()
        .forEach((code, currency) -> ensure(new Country(code.trim()), true, currency.trim()));
  }

  /**
   * Serves a country in its currency. Calling it again updates whether it is active and its
   * currency.
   */
  public synchronized void ensure(Country country, boolean active, String currency) {
    String code = Currency.getInstance(currency).getCurrencyCode();
    jdbc.sql(
            """
            insert into served_country (code, active, currency) values (:code, :active, :currency)
            on conflict (code) do update set active = excluded.active, currency = excluded.currency
            """)
        .param("code", country.code())
        .param("active", active)
        .param("currency", code)
        .update();
    countries.register(country, active, code);
  }
}
