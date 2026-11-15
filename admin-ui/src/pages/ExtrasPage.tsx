import {
  Button,
  Card,
  CardContent,
  FormControlLabel,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { ApiError, ancillary, unwrap } from "../api/clients";
import { countryHeader } from "../api/queries";
import type { Extra } from "../api/types";
import { ActiveChip } from "../components/ActiveChip";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";
import { formatMoney } from "../money";

type Form = {
  code: string;
  name: string;
  kind: Extra["kind"];
  charge: Extra["charge"];
  price: string;
  maxQuantity: string;
  active: boolean;
};

const KINDS = { EQUIPMENT: "Equipment", SERVICE: "Service" } as const;
const CHARGES = { PER_DAY: "Per day", PER_RENTAL: "Per rental" } as const;

/** The extras of a country with their prices, and the fee for returning to another location. */
export function ExtrasPage() {
  return <NeedsCountry>{(country) => <ExtrasOfCountry country={country} />}</NeedsCountry>;
}

function ExtrasOfCountry({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const params = countryHeader(country);
  const extras = useQuery({
    queryKey: ["extras", country],
    queryFn: () => unwrap(ancillary.GET("/api/v1/admin/extras", { params })),
  });
  const [editing, setEditing] = useState<{ value: Form; isNew: boolean } | null>(null);
  const save = useMutation({
    mutationFn: (form: Form) =>
      unwrap(
        ancillary.PUT("/api/v1/admin/extras/{code}", {
          params: { path: { code: form.code.trim().toUpperCase() }, ...params },
          body: {
            name: form.name,
            kind: form.kind,
            charge: form.charge,
            price: form.price.trim().replace(",", "."),
            maxQuantity: Number(form.maxQuantity),
            active: form.active,
          },
        }),
      ),
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: ["extras", country] });
    },
  });
  const open = (value: Form, isNew: boolean) => {
    save.reset();
    setEditing({ value, isNew });
  };
  const change = (patch: Partial<Form>) =>
    setEditing((current) => current && { ...current, value: { ...current.value, ...patch } });

  return (
    <>
      <PageHeader
        title="Extras"
        action={{
          label: "Add extra",
          onClick: () =>
            open(
              {
                code: "",
                name: "",
                kind: "EQUIPMENT",
                charge: "PER_DAY",
                price: "",
                maxQuantity: "1",
                active: true,
              },
              true,
            ),
        }}
      />
      <OneWayFee country={country} />
      <ErrorAlert error={extras.error} />
      <ResponsiveList<Extra>
        items={extras.data ?? []}
        rowKey={(e) => e.code}
        empty={extras.isLoading ? "Loading…" : "No extras yet."}
        columns={[
          { label: "Name", render: (e) => e.name },
          { label: "Kind", render: (e) => KINDS[e.kind] },
          {
            label: "Price",
            render: (e) => `${formatMoney(e.price)} ${CHARGES[e.charge].toLowerCase()}`,
          },
          { label: "Up to", render: (e) => e.maxQuantity },
          { label: "Status", render: (e) => <ActiveChip active={e.active} /> },
        ]}
        actions={(e) => (
          <Button
            onClick={() =>
              open(
                {
                  code: e.code,
                  name: e.name,
                  kind: e.kind,
                  charge: e.charge,
                  price: e.price.amount,
                  maxQuantity: String(e.maxQuantity),
                  active: e.active,
                },
                false,
              )
            }
            aria-label={`Edit ${e.name}`}
            sx={{ minHeight: 44 }}
          >
            Edit
          </Button>
        )}
      />
      {editing && (
        <FormDialog
          title={editing.isNew ? "Add extra" : `Edit ${editing.value.name}`}
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing.value)}
        >
          <TextField
            label="Code"
            value={editing.value.code}
            disabled={!editing.isNew}
            onChange={(e) => change({ code: e.target.value })}
            helperText="Upper-case letters, digits and _, for example CHILD_SEAT"
            required
          />
          <TextField
            label="Name"
            value={editing.value.name}
            onChange={(e) => change({ name: e.target.value })}
            required
          />
          <TextField
            select
            id="extra-kind"
            label="Kind"
            value={editing.value.kind}
            onChange={(e) => change({ kind: e.target.value as Form["kind"] })}
          >
            {Object.entries(KINDS).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            id="extra-charge"
            label="Charged"
            value={editing.value.charge}
            onChange={(e) => change({ charge: e.target.value as Form["charge"] })}
          >
            {Object.entries(CHARGES).map(([value, label]) => (
              <MenuItem key={value} value={value}>
                {label}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Price"
            inputMode="decimal"
            value={editing.value.price}
            onChange={(e) => change({ price: e.target.value })}
            helperText="In the currency of the country"
            required
          />
          <TextField
            label="Most per reservation"
            type="number"
            inputMode="numeric"
            value={editing.value.maxQuantity}
            onChange={(e) => change({ maxQuantity: e.target.value })}
            required
          />
          <FormControlLabel
            control={
              <Switch
                checked={editing.value.active}
                onChange={(e) => change({ active: e.target.checked })}
              />
            }
            label="Offered to customers"
          />
        </FormDialog>
      )}
    </>
  );
}

/** The fixed fee for returning the car to another location of the country. */
function OneWayFee({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const params = countryHeader(country);
  const fee = useQuery({
    queryKey: ["one-way-fee", country],
    queryFn: async () => {
      try {
        return await unwrap(ancillary.GET("/api/v1/admin/one-way-fee", { params }));
      } catch (error) {
        if (error instanceof ApiError && error.status === 404) {
          return null;
        }
        throw error;
      }
    },
  });
  const [amount, setAmount] = useState<string | null>(null);
  const save = useMutation({
    mutationFn: (value: string) =>
      unwrap(
        ancillary.PUT("/api/v1/admin/one-way-fee", {
          params,
          body: { amount: value.trim().replace(",", ".") },
        }),
      ),
    onSuccess: () => {
      setAmount(null);
      return queryClient.invalidateQueries({ queryKey: ["one-way-fee", country] });
    },
  });

  return (
    <Card variant="outlined" sx={{ mb: 3 }}>
      <CardContent>
        <Stack
          direction={{ xs: "column", sm: "row" }}
          spacing={2}
          sx={{ alignItems: { sm: "center" }, justifyContent: "space-between" }}
        >
          <div>
            <Typography variant="h6" component="h2">
              One-way fee
            </Typography>
            <Typography color="text.secondary">
              {fee.data
                ? `${formatMoney(fee.data)} when the car is returned to another location`
                : fee.isLoading
                  ? "Loading…"
                  : "Not set: one-way rentals cannot be priced"}
            </Typography>
          </div>
          <Button
            variant="outlined"
            onClick={() => {
              save.reset();
              setAmount(fee.data?.amount ?? "");
            }}
            sx={{ minHeight: 44 }}
          >
            Change one-way fee
          </Button>
        </Stack>
        <ErrorAlert error={fee.error} />
      </CardContent>
      {amount !== null && (
        <FormDialog
          title="One-way fee"
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setAmount(null)}
          onSubmit={() => save.mutate(amount)}
        >
          <TextField
            label="Fee"
            inputMode="decimal"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            helperText="In the currency of the country"
            required
          />
        </FormDialog>
      )}
    </Card>
  );
}
