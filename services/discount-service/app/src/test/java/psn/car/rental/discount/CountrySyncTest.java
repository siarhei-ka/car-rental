package psn.car.rental.discount;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import psn.car.rental.discount.tenant.Countries;
import psn.car.rental.discount.tenant.Country;

/** Countries announced by the administration service are served with their currency. */
@IntegrationTest
@DisplayName("Country list")
class CountrySyncTest {

  @Autowired KafkaTemplate<String, String> kafka;
  @Autowired Countries countries;
  @Autowired JdbcClient jdbc;

  @Test
  void aNewCountryIsServedInItsCurrencyOnceItsEventArrives() throws Exception {
    kafka.send(
        "reference-data",
        "country:cz",
        """
        {"eventId":"c1","kind":"COUNTRY","action":"SAVED",
         "data":{"code":"cz","name":"Czechia","currency":"CZK","active":true}}
        """);

    waitUntil(() -> countries.isServed(new Country("cz")));
    assertThat(countries.currencyOf(new Country("cz"))).isEqualTo("CZK");
    assertThat(
            jdbc.sql("select currency from served_country where code = 'cz'")
                .query(String.class)
                .single())
        .isEqualTo("CZK");
    assertThat(
            jdbc.sql("select to_regclass('tenant_cz.discount_rule') is not null")
                .query(Boolean.class)
                .single())
        .isTrue();
  }

  @Test
  void aCountryWithoutAValidCurrencyIsIgnored() throws Exception {
    kafka.send("reference-data", "x", "not json");
    kafka.send(
        "reference-data",
        "country:sk",
        "{\"kind\":\"COUNTRY\",\"data\":{\"code\":\"sk\",\"active\":true}}");
    kafka.send(
        "reference-data",
        "country:hu",
        "{\"kind\":\"COUNTRY\",\"data\":{\"code\":\"hu\",\"currency\":\"HUF\",\"active\":true}}");

    waitUntil(() -> countries.isServed(new Country("hu")));
    assertThat(countries.isServed(new Country("sk"))).isFalse();
  }

  private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("The condition was not met within 30 seconds");
      }
      Thread.sleep(100);
    }
  }
}
