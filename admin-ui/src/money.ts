import type { Money } from "./api/types";

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
