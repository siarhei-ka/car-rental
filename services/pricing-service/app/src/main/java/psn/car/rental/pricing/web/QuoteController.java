package psn.car.rental.pricing.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import psn.car.rental.pricing.application.QuoteService;
import psn.car.rental.pricing.application.QuoteService.ExtraChoice;
import psn.car.rental.pricing.application.QuoteService.QuoteRequest;
import psn.car.rental.pricing.domain.Charge;
import psn.car.rental.pricing.domain.LineKind;
import psn.car.rental.pricing.domain.Money;
import psn.car.rental.pricing.domain.Quote;
import psn.car.rental.pricing.domain.QuoteLine;

/**
 * The final price for the signed-in customer. The customer is the subject of the access token, so
 * the price is always for the customer's own driver profile. Other services, such as the
 * reservation service, ask the same price for a customer they act for.
 */
@RestController
class QuoteController {

  private final QuoteService quotes;

  QuoteController(QuoteService quotes) {
    this.quotes = quotes;
  }

  @PostMapping("/api/v1/quotes")
  QuoteResponse quote(
      @AuthenticationPrincipal Jwt token,
      @RequestHeader(name = "X-Country", required = false) String country,
      @Valid @RequestBody QuoteBody body) {
    return QuoteResponse.of(quotes.quoteForCustomer(token.getSubject(), request(country, body)));
  }

  /** The same price for a customer that another service acts for. Only for other services. */
  @PostMapping("/api/v1/customers/{customerId}/quotes")
  QuoteResponse quoteForCustomer(
      @PathVariable String customerId,
      @RequestHeader(name = "X-Country", required = false) String country,
      @Valid @RequestBody QuoteBody body) {
    return QuoteResponse.of(quotes.quote(customerId, request(country, body)));
  }

  private static QuoteRequest request(String country, QuoteBody body) {
    return new QuoteRequest(
        country,
        body.pickUpLocationId(),
        body.returnLocationId(),
        body.start(),
        body.days(),
        body.carType(),
        body.carClass(),
        body.insurance(),
        body.extras() == null
            ? List.of()
            : body.extras().stream().map(e -> new ExtraChoice(e.code(), e.quantity())).toList());
  }

  record QuoteBody(
      @NotNull String pickUpLocationId,
      String returnLocationId,
      @NotNull Instant start,
      @Min(1) @Max(365) int days,
      @NotNull String carType,
      @NotNull String carClass,
      @NotNull String insurance,
      @Valid List<ExtraBody> extras) {}

  record ExtraBody(@NotNull String code, @Min(1) int quantity) {}

  record QuoteResponse(
      String currency,
      List<LineResponse> lines,
      MoneyResponse subtotal,
      DiscountResponse discount,
      MoneyResponse total) {

    static QuoteResponse of(Quote quote) {
      return new QuoteResponse(
          quote.currency().getCurrencyCode(),
          quote.lines().stream().map(LineResponse::of).toList(),
          MoneyResponse.of(quote.subtotal()),
          quote
              .discount()
              .map(d -> new DiscountResponse(d.name(), MoneyResponse.of(d.amount())))
              .orElse(null),
          MoneyResponse.of(quote.total()));
    }
  }

  record LineResponse(
      LineKind kind,
      String code,
      String name,
      int quantity,
      MoneyResponse unitPrice,
      Charge charge,
      MoneyResponse amount) {

    static LineResponse of(QuoteLine line) {
      return new LineResponse(
          line.kind(),
          line.code(),
          line.name(),
          line.quantity(),
          MoneyResponse.of(line.unitPrice()),
          line.charge(),
          MoneyResponse.of(line.amount()));
    }
  }

  record DiscountResponse(String name, MoneyResponse amount) {}

  record MoneyResponse(String amount, String currency) {

    static MoneyResponse of(Money money) {
      return new MoneyResponse(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
  }
}
