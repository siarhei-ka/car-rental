/** An amount as the services send it: a decimal string and an ISO 4217 currency. */
export type Money = { amount: string; currency: string };

/** Shows an amount with its currency, such as "PLN 149.00". The amount stays a decimal string. */
export function formatMoney(money: Money): string {
  try {
    return new Intl.NumberFormat("en", { style: "currency", currency: money.currency }).format(
      Number(money.amount),
    );
  } catch {
    return `${money.amount} ${money.currency}`;
  }
}
