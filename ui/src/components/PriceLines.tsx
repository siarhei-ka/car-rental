import { Divider, Stack, Typography } from "@mui/material";
import { formatMoney, type Money } from "../money";

type Line = {
  kind: "CAR_RENTAL" | "INSURANCE" | "EXTRA" | "ONE_WAY_FEE";
  name: string;
  quantity: number;
  unitPrice: Money;
  charge: "PER_DAY" | "PER_RENTAL";
  amount: Money;
};

/** A price as the pricing service calculates it, for a quote or a stored reservation. */
export type PriceView = {
  lines: Line[];
  subtotal: Money;
  discount?: { name: string; amount: Money } | null;
  total: Money;
};

/** Every line of a price, the discount and the total due at pick-up. */
export function PriceLines({ price }: { price: PriceView }) {
  return (
    <Stack component="dl" spacing={1} sx={{ m: 0 }} aria-label="Price">
      {price.lines.map((line, index) => (
        <Row key={index} label={describe(line)} amount={formatMoney(line.amount)} />
      ))}
      {price.discount && (
        <>
          <Divider />
          <Row label="Before discount" amount={formatMoney(price.subtotal)} />
          <Row
            label={`Discount: ${price.discount.name}`}
            amount={`−${formatMoney(price.discount.amount)}`}
          />
        </>
      )}
      <Divider />
      <Row label="Total, due at pick-up" amount={formatMoney(price.total)} strong />
    </Stack>
  );
}

function Row({ label, amount, strong }: { label: string; amount: string; strong?: boolean }) {
  const weight = strong ? 600 : undefined;
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: "space-between" }}>
      <Typography component="dt" sx={{ fontWeight: weight }}>
        {label}
      </Typography>
      <Typography component="dd" sx={{ m: 0, fontWeight: weight, whiteSpace: "nowrap" }}>
        {amount}
      </Typography>
    </Stack>
  );
}

/** A line as the customer reads it, such as "Child seat × 2, PLN 25.00 per day". */
function describe(line: Line): string {
  const unit = formatMoney(line.unitPrice);
  switch (line.kind) {
    case "CAR_RENTAL":
      return `Car rental, ${unit} per day`;
    case "INSURANCE":
      return `${line.name}, ${unit} per day`;
    case "EXTRA": {
      const each = line.charge === "PER_DAY" ? `${unit} per day` : `${unit} per rental`;
      return line.quantity > 1
        ? `${line.name} × ${line.quantity}, ${each}`
        : `${line.name}, ${each}`;
    }
    case "ONE_WAY_FEE":
      return "One-way fee";
  }
}
