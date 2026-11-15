import { Button, Chip, MenuItem, TextField } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { pricing, unwrap } from "../api/clients";
import { countryHeader, useCarClasses, useCarTypes, useLocations } from "../api/queries";
import type { Money } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";
import { formatMoney } from "../money";

/** A car type and class of the chosen location, with its daily rate if it has one. */
type Row = {
  carType: string;
  carTypeName: string;
  carClass: string;
  carClassName: string;
  rate?: Money;
};

/**
 * The daily rates of a location. Every active car type and class is listed, so that a car without
 * a rate stands out: the search does not offer it.
 */
export function RatesPage() {
  return <NeedsCountry>{(country) => <RatesOfCountry country={country} />}</NeedsCountry>;
}

function RatesOfCountry({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const locations = useLocations(country);
  const carTypes = useCarTypes();
  const carClasses = useCarClasses();
  const [chosen, setChosen] = useState("");
  const location = chosen || locations.data?.[0]?.id || "";
  const rates = useQuery({
    queryKey: ["rates", country, location],
    enabled: location !== "",
    queryFn: () =>
      unwrap(
        pricing.GET("/api/v1/admin/rates", {
          params: { query: { locationId: location }, ...countryHeader(country) },
        }),
      ),
  });
  const [editing, setEditing] = useState<{ row: Row; amount: string } | null>(null);
  const save = useMutation({
    mutationFn: ({ row, amount }: { row: Row; amount: string }) =>
      unwrap(
        pricing.PUT("/api/v1/admin/rates/{locationId}/{carType}/{carClass}", {
          params: {
            path: { locationId: location, carType: row.carType, carClass: row.carClass },
            ...countryHeader(country),
          },
          body: { amount: amount.trim().replace(",", ".") },
        }),
      ),
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: ["rates", country, location] });
    },
  });

  const rows: Row[] = (carTypes.data ?? [])
    .filter((t) => t.active)
    .flatMap((t) =>
      (carClasses.data ?? [])
        .filter((c) => c.active)
        .map((c) => ({
          carType: t.code,
          carTypeName: t.name,
          carClass: c.code,
          carClassName: c.name,
          rate: rates.data?.find((r) => r.carType === t.code && r.carClass === c.code)?.dailyRate,
        })),
    );
  const open = (row: Row) => {
    save.reset();
    setEditing({ row, amount: row.rate?.amount ?? "" });
  };

  return (
    <>
      <PageHeader title="Daily rates" />
      <ErrorAlert error={locations.error ?? carTypes.error ?? carClasses.error ?? rates.error} />
      <TextField
        select
        id="rates-location"
        label="Location"
        value={location}
        onChange={(e) => setChosen(e.target.value)}
        sx={{ mb: 2, minWidth: { xs: "100%", sm: 280 } }}
      >
        {(locations.data ?? []).map((l) => (
          <MenuItem key={l.id} value={l.id}>
            {l.name}
          </MenuItem>
        ))}
      </TextField>
      <ResponsiveList<Row>
        items={rates.isSuccess ? rows : []}
        rowKey={(r) => `${r.carType}/${r.carClass}`}
        empty={rates.isLoading ? "Loading…" : "No active car types and classes."}
        columns={[
          { label: "Car type", render: (r) => r.carTypeName },
          { label: "Car class", render: (r) => r.carClassName },
          {
            label: "Daily rate",
            render: (r) =>
              r.rate ? (
                formatMoney(r.rate)
              ) : (
                <Chip size="small" color="warning" label="No rate: not offered" />
              ),
          },
        ]}
        actions={(r) => (
          <Button
            onClick={() => open(r)}
            aria-label={`Set rate of ${r.carTypeName} ${r.carClassName}`}
            sx={{ minHeight: 44 }}
          >
            {r.rate ? "Change" : "Set rate"}
          </Button>
        )}
      />
      {editing && (
        <FormDialog
          title={`Daily rate of ${editing.row.carTypeName} ${editing.row.carClassName}`}
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing)}
        >
          <TextField
            label="Price per day"
            inputMode="decimal"
            value={editing.amount}
            onChange={(e) => setEditing({ ...editing, amount: e.target.value })}
            helperText="In the currency of the country, for example 149.00"
            required
          />
        </FormDialog>
      )}
    </>
  );
}
