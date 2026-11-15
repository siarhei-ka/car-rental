package psn.car.rental.pricing.web;

import psn.car.rental.pricing.domain.DailyRate;
import psn.car.rental.pricing.domain.Money;

/** A daily rate as the API shows it. */
record RateResponse(String locationId, String carType, String carClass, MoneyResponse dailyRate) {

  static RateResponse of(DailyRate rate) {
    return new RateResponse(
        rate.key().locationId(),
        rate.key().carType(),
        rate.key().carClass(),
        MoneyResponse.of(rate.price()));
  }

  /** An amount as a decimal string, so that no client turns it into a floating-point number. */
  record MoneyResponse(String amount, String currency) {

    static MoneyResponse of(Money money) {
      return new MoneyResponse(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
  }
}
