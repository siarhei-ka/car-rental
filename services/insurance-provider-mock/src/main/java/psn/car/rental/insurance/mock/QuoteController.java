package psn.car.rental.insurance.mock;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** The one operation of the provider: the price per day of an insurance product. */
@RestController
class QuoteController {

  private final PriceRules rules;
  private final MockProperties properties;

  QuoteController(PriceRules rules, MockProperties properties) {
    this.rules = rules;
    this.properties = properties;
  }

  @PostMapping("/v1/quotes")
  PriceRules.Quote quote(
      @RequestHeader(name = "X-Api-Key", required = false) String apiKey,
      @Valid @RequestBody QuoteBody body)
      throws InterruptedException {
    if (!knownKey(apiKey)) {
      throw new WrongApiKeyException();
    }
    if (!properties.delay().isZero()) {
      Thread.sleep(properties.delay());
    }
    return rules.quote(
        new PriceRules.QuoteRequest(
            body.product(),
            body.rentalCountry(),
            body.currency(),
            body.driverAge(),
            body.licenceCountry()));
  }

  private boolean knownKey(String apiKey) {
    if (apiKey == null || properties.apiKey() == null || properties.apiKey().isBlank()) {
      return false;
    }
    return MessageDigest.isEqual(
        apiKey.getBytes(StandardCharsets.UTF_8),
        properties.apiKey().getBytes(StandardCharsets.UTF_8));
  }

  record QuoteBody(
      @NotNull String product,
      @NotNull @Pattern(regexp = "[a-z]{2}") String rentalCountry,
      @NotNull @Pattern(regexp = "[A-Z]{3}") String currency,
      @Min(16) @Max(120) Integer driverAge,
      @Pattern(regexp = "[a-z]{2}") String licenceCountry) {}

  static class WrongApiKeyException extends RuntimeException {}

  @ExceptionHandler(WrongApiKeyException.class)
  ProblemDetail wrongKey() {
    return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid X-Api-Key is needed");
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail invalid(IllegalArgumentException e) {
    return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
  }
}
