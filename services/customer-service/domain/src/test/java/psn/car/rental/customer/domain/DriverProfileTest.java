package psn.car.rental.customer.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Driver profile")
class DriverProfileTest {

  private static final CustomerId CUSTOMER = new CustomerId("user-1");
  private static final LicenceCountry POLAND = new LicenceCountry("pl");
  private static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

  @Nested
  @DisplayName("Entering a profile")
  class Entering {

    @Test
    void acceptsAnAdultDriver() {
      DriverProfile profile =
          DriverProfile.enter(CUSTOMER, LocalDate.of(1990, 5, 17), POLAND, TODAY);

      assertThat(profile.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 17));
      assertThat(profile.licenceCountry().code()).isEqualTo("pl");
    }

    @Test
    void acceptsADriverOnTheirEighteenthBirthday() {
      assertThat(DriverProfile.enter(CUSTOMER, TODAY.minusYears(18), POLAND, TODAY).ageOn(TODAY))
          .isEqualTo(18);
    }

    @Test
    void refusesADriverOneDayBeforeTheirEighteenthBirthday() {
      assertThatThrownBy(
              () -> DriverProfile.enter(CUSTOMER, TODAY.minusYears(18).plusDays(1), POLAND, TODAY))
          .isInstanceOf(InvalidProfileException.class)
          .hasMessageContaining("at least 18");
    }

    @Test
    void refusesADateOfBirthInTheFuture() {
      assertThatThrownBy(() -> DriverProfile.enter(CUSTOMER, TODAY.plusDays(1), POLAND, TODAY))
          .isInstanceOf(InvalidProfileException.class)
          .hasMessageContaining("future");
    }

    @Test
    void refusesAnImpossibleAge() {
      assertThatThrownBy(() -> DriverProfile.enter(CUSTOMER, TODAY.minusYears(121), POLAND, TODAY))
          .isInstanceOf(InvalidProfileException.class)
          .hasMessageContaining("at most 120");
    }

    @Test
    void requiresADateOfBirth() {
      assertThatThrownBy(() -> DriverProfile.enter(CUSTOMER, null, POLAND, TODAY))
          .isInstanceOf(InvalidProfileException.class)
          .hasMessageContaining("required");
    }
  }

  @Nested
  @DisplayName("Age")
  class Age {

    @Test
    void countsFullYearsOnTheDay() {
      DriverProfile profile = new DriverProfile(CUSTOMER, LocalDate.of(2000, 6, 15), POLAND);

      assertThat(profile.ageOn(LocalDate.of(2026, 6, 14))).isEqualTo(25);
      assertThat(profile.ageOn(LocalDate.of(2026, 6, 15))).isEqualTo(26);
    }

    @Test
    void givesSomeoneBornOnTheTwentyNinthOfFebruaryTheirBirthdayOnTheFirstOfMarch() {
      DriverProfile profile = new DriverProfile(CUSTOMER, LocalDate.of(2004, 2, 29), POLAND);

      assertThat(profile.ageOn(LocalDate.of(2025, 2, 28))).isEqualTo(20);
      assertThat(profile.ageOn(LocalDate.of(2025, 3, 1))).isEqualTo(21);
      assertThat(profile.ageOn(LocalDate.of(2028, 2, 29))).isEqualTo(24);
    }
  }

  @Nested
  @DisplayName("Licence country and customer")
  class Values {

    @Test
    void acceptsAnyIsoCountryInAnyCaseAndWritesItInLowerCase() {
      assertThat(new LicenceCountry("US").code()).isEqualTo("us");
      assertThat(new LicenceCountry(" ua ").code()).isEqualTo("ua");
    }

    @Test
    void refusesACodeThatIsNoCountry() {
      for (String code : new String[] {"xx", "pol", "", null}) {
        assertThatThrownBy(() -> new LicenceCountry(code))
            .as(String.valueOf(code))
            .isInstanceOf(InvalidProfileException.class);
      }
    }

    @Test
    void refusesAnEmptyOrLongCustomerId() {
      assertThatThrownBy(() -> new CustomerId(" ")).isInstanceOf(InvalidProfileException.class);
      assertThatThrownBy(() -> new CustomerId("x".repeat(256)))
          .isInstanceOf(InvalidProfileException.class);
      assertThatThrownBy(() -> new CustomerId(null)).isInstanceOf(InvalidProfileException.class);
    }
  }
}
