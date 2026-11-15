import {
  Autocomplete,
  Button,
  FormControlLabel,
  MenuItem,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ancillary, discount, unwrap } from "../api/clients";
import { countryHeader } from "../api/queries";
import type { DiscountRule, DiscountRuleRequest } from "../api/types";
import { ActiveChip } from "../components/ActiveChip";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";
import { formatMoney } from "../money";

type Kind = DiscountRuleRequest["effect"]["kind"];
type Target = DiscountRuleRequest["effect"]["target"];

type Form = {
  id: string | null;
  name: string;
  minExtras: string;
  requiredExtras: string[];
  minDays: string;
  kind: Kind;
  value: string;
  target: Target;
  validFrom: string;
  validTo: string;
  active: boolean;
};

const KINDS: Record<Kind, string> = { PERCENT_OFF: "Percentage off", AMOUNT_OFF: "Amount off" };
const TARGETS: Record<Target, string> = { EXTRAS: "the extras", TOTAL: "the total" };

/**
 * The discount rules of a country. A rule applies when all its conditions hold, and only the rule
 * that saves the customer the most is used. Rules are switched off, never deleted.
 */
export function DiscountsPage() {
  return <NeedsCountry>{(country) => <DiscountsOfCountry country={country} />}</NeedsCountry>;
}

function DiscountsOfCountry({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const params = countryHeader(country);
  const rules = useQuery({
    queryKey: ["discount-rules", country],
    queryFn: () => unwrap(discount.GET("/api/v1/admin/discount-rules", { params })),
  });
  // The extras of the country, to choose the ones a rule requires.
  const extras = useQuery({
    queryKey: ["extras", country],
    queryFn: () => unwrap(ancillary.GET("/api/v1/admin/extras", { params })),
  });
  const extraName = (code: string) => extras.data?.find((e) => e.code === code)?.name ?? code;

  const [editing, setEditing] = useState<Form | null>(null);
  const save = useMutation({
    mutationFn: (form: Form) => {
      const body = toRequest(form);
      return unwrap(
        form.id
          ? discount.PUT("/api/v1/admin/discount-rules/{id}", {
              params: { path: { id: form.id }, ...params },
              body,
            })
          : discount.POST("/api/v1/admin/discount-rules", { params, body }),
      );
    },
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: ["discount-rules", country] });
    },
  });
  const open = (form: Form) => {
    save.reset();
    setEditing(form);
  };
  const change = (patch: Partial<Form>) =>
    setEditing((current) => current && { ...current, ...patch });

  return (
    <>
      <PageHeader
        title="Discounts"
        action={{ label: "Add rule", onClick: () => open(emptyForm()) }}
      />
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        A rule applies when all its conditions hold. When several rules apply, only the one that
        saves the customer the most is used.
      </Typography>
      <ErrorAlert error={rules.error} />
      <ResponsiveList<DiscountRule>
        items={rules.data ?? []}
        rowKey={(r) => r.id}
        empty={rules.isLoading ? "Loading…" : "No discount rules yet."}
        columns={[
          { label: "Name", render: (r) => r.name },
          { label: "When", render: (r) => describeConditions(r, extraName) },
          { label: "Discount", render: describeEffect },
          { label: "Valid", render: describeValidity },
          { label: "Status", render: (r) => <ActiveChip active={r.active} /> },
        ]}
        actions={(r) => (
          <Button
            onClick={() => open(fromRule(r))}
            aria-label={`Edit ${r.name}`}
            sx={{ minHeight: 44 }}
          >
            Edit
          </Button>
        )}
      />
      {editing && (
        <FormDialog
          title={editing.id ? `Edit ${editing.name}` : "Add rule"}
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing)}
        >
          <TextField
            label="Name"
            value={editing.name}
            onChange={(e) => change({ name: e.target.value })}
            helperText="For example: 3 or more extras: 10% off the extras"
            required
          />
          <Typography variant="subtitle2" component="h3">
            Conditions, at least one
          </Typography>
          <TextField
            label="At least this many extras"
            type="number"
            inputMode="numeric"
            value={editing.minExtras}
            onChange={(e) => change({ minExtras: e.target.value })}
            helperText="Counted with their quantities. Empty for no condition"
          />
          <Autocomplete
            multiple
            options={(extras.data ?? []).map((e) => e.code)}
            getOptionLabel={extraName}
            value={editing.requiredExtras}
            onChange={(_, value) => change({ requiredExtras: value })}
            renderInput={(input) => (
              <TextField
                {...input}
                label="These extras together"
                helperText="All of them must be chosen. Empty for no condition"
              />
            )}
          />
          <TextField
            label="At least this many days"
            type="number"
            inputMode="numeric"
            value={editing.minDays}
            onChange={(e) => change({ minDays: e.target.value })}
            helperText="Empty for no condition"
          />
          <Typography variant="subtitle2" component="h3">
            Discount
          </Typography>
          <TextField
            select
            id="discount-kind"
            label="Kind"
            value={editing.kind}
            onChange={(e) => change({ kind: e.target.value as Kind })}
          >
            {Object.entries(KINDS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label={editing.kind === "PERCENT_OFF" ? "Percentage" : "Amount"}
            inputMode="decimal"
            value={editing.value}
            onChange={(e) => change({ value: e.target.value })}
            helperText={
              editing.kind === "PERCENT_OFF"
                ? "More than 0 and at most 100"
                : "In the currency of the country"
            }
            required
          />
          <TextField
            select
            id="discount-target"
            label="Taken off"
            value={editing.target}
            onChange={(e) => change({ target: e.target.value as Target })}
          >
            {Object.entries(TARGETS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="First day"
            type="date"
            value={editing.validFrom}
            onChange={(e) => change({ validFrom: e.target.value })}
            slotProps={{ inputLabel: { shrink: true } }}
            required
          />
          <TextField
            label="Last day"
            type="date"
            value={editing.validTo}
            onChange={(e) => change({ validTo: e.target.value })}
            helperText="Empty for no end. Checked against the day the price is calculated"
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <FormControlLabel
            control={
              <Switch
                checked={editing.active}
                onChange={(e) => change({ active: e.target.checked })}
              />
            }
            label="Switched on"
          />
        </FormDialog>
      )}
    </>
  );
}

function emptyForm(): Form {
  return {
    id: null,
    name: "",
    minExtras: "",
    requiredExtras: [],
    minDays: "",
    kind: "PERCENT_OFF",
    value: "",
    target: "EXTRAS",
    validFrom: new Date().toISOString().slice(0, 10),
    validTo: "",
    active: true,
  };
}

function fromRule(rule: DiscountRule): Form {
  return {
    id: rule.id,
    name: rule.name,
    minExtras: rule.conditions.minExtras?.toString() ?? "",
    requiredExtras: [...(rule.conditions.requiredExtras ?? [])].sort(),
    minDays: rule.conditions.minDays?.toString() ?? "",
    kind: rule.effect.kind,
    value:
      (rule.effect.kind === "PERCENT_OFF" ? rule.effect.percent : rule.effect.amount?.amount) ?? "",
    target: rule.effect.target,
    validFrom: rule.validFrom,
    validTo: rule.validTo ?? "",
    active: rule.active,
  };
}

function toRequest(form: Form): DiscountRuleRequest {
  const value = form.value.trim().replace(",", ".");
  const whole = (text: string) => (text.trim() === "" ? null : Number(text));
  return {
    name: form.name,
    conditions: {
      minExtras: whole(form.minExtras),
      requiredExtras: form.requiredExtras,
      minDays: whole(form.minDays),
    },
    effect: {
      kind: form.kind,
      percent: form.kind === "PERCENT_OFF" ? value : null,
      amount: form.kind === "AMOUNT_OFF" ? value : null,
      target: form.target,
    },
    validFrom: form.validFrom,
    validTo: form.validTo === "" ? null : form.validTo,
    active: form.active,
  };
}

function describeConditions(rule: DiscountRule, extraName: (code: string) => string): string {
  const parts: string[] = [];
  const { minExtras, requiredExtras, minDays } = rule.conditions;
  if (minExtras != null) parts.push(`${minExtras} or more extras`);
  if (requiredExtras && requiredExtras.length > 0)
    parts.push([...requiredExtras].sort().map(extraName).join(" and "));
  if (minDays != null) parts.push(`${minDays} or more days`);
  return parts.join(", ");
}

function describeEffect(rule: DiscountRule): string {
  const off =
    rule.effect.kind === "PERCENT_OFF"
      ? `${rule.effect.percent}%`
      : rule.effect.amount
        ? formatMoney(rule.effect.amount)
        : "";
  return `${off} off ${TARGETS[rule.effect.target]}`;
}

function describeValidity(rule: DiscountRule): string {
  return rule.validTo ? `${rule.validFrom} to ${rule.validTo}` : `From ${rule.validFrom}`;
}
