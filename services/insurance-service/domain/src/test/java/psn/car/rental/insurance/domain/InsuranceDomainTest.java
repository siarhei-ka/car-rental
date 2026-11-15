package psn.car.rental.insurance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Insurance domain")
class InsuranceDomainTest {

  @Test
  void anOptionHasACodeAndAName() {
    InsuranceOption basic = new InsuranceOption("BASIC", "Basic cover", null, true, 1);

    assertThat(basic.description()).isEmpty();
    assertThatThrownBy(() -> new InsuranceOption("basic", "Basic", "", false, 1))
        .isInstanceOf(InvalidInsuranceException.class);
    assertThatThrownBy(() -> new InsuranceOption("BASIC", " ", "", false, 1))
        .isInstanceOf(InvalidInsuranceException.class);
  }

  @Test
  void aDriverIsAnAdultWithALicenceCountry() {
    assertThat(new Driver(24, "pl").age()).isEqualTo(24);
    assertThatThrownBy(() -> new Driver(17, "pl"))
        .isInstanceOf(InvalidInsuranceException.class)
        .hasMessageContaining("18 to 120");
    assertThatThrownBy(() -> new Driver(30, "PL")).isInstanceOf(InvalidInsuranceException.class);
    assertThatThrownBy(() -> new Driver(30, null)).isInstanceOf(InvalidInsuranceException.class);
  }

  @Test
  void moneyKeepsTheMinorUnitsOfItsCurrency() {
    assertThat(Money.of("10", "PLN")).hasToString("10.00 PLN");
    assertThat(Money.of("15", "JPY")).hasToString("15 JPY");
    assertThat(Money.of("10", "EUR").times(3)).isEqualTo(Money.of("30.00", "EUR"));
    assertThat(Money.of("10", "EUR").isPositive()).isTrue();
    assertThatThrownBy(() -> Money.of("1.001", "EUR"))
        .isInstanceOf(InvalidInsuranceException.class);
    assertThatThrownBy(() -> Money.of("one", "EUR")).isInstanceOf(InvalidInsuranceException.class);
    assertThatThrownBy(() -> Money.of("1", "QQQ")).isInstanceOf(InvalidInsuranceException.class);
  }
}
