package psn.car.rental.customer.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.customer.application.ProfileService;
import psn.car.rental.customer.domain.CustomerId;
import psn.car.rental.customer.domain.DriverProfile;
import psn.car.rental.customer.domain.LicenceCountry;

/**
 * The driver profile of the signed-in customer. The customer is the subject of the access token, so
 * a customer can never name another customer's profile. Other services read a customer's profile by
 * the customer's ID.
 */
@RestController
class ProfileController {

  private final ProfileService profiles;

  ProfileController(ProfileService profiles) {
    this.profiles = profiles;
  }

  @GetMapping("/api/v1/profile")
  ProfileResponse get(@AuthenticationPrincipal Jwt token) {
    return ProfileResponse.of(profiles.get(customer(token)));
  }

  @PutMapping("/api/v1/profile")
  ProfileResponse save(
      @AuthenticationPrincipal Jwt token, @Valid @RequestBody ProfileRequest body) {
    return ProfileResponse.of(
        profiles.save(
            customer(token), body.dateOfBirth(), new LicenceCountry(body.licenceCountry())));
  }

  /**
   * The driver profile of any customer, for other services that act for that customer, such as the
   * pricing service when it prices insurance for the driver.
   */
  @GetMapping("/api/v1/profiles/{customerId}")
  ProfileResponse getForService(@PathVariable String customerId) {
    return ProfileResponse.of(profiles.get(new CustomerId(customerId)));
  }

  private static CustomerId customer(Jwt token) {
    return new CustomerId(token.getSubject());
  }

  record ProfileRequest(@NotNull LocalDate dateOfBirth, @NotNull String licenceCountry) {}

  record ProfileResponse(LocalDate dateOfBirth, String licenceCountry) {

    static ProfileResponse of(DriverProfile profile) {
      return new ProfileResponse(profile.dateOfBirth(), profile.licenceCountry().code());
    }
  }
}
