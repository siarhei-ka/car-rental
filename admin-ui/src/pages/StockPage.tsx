import { Button, MenuItem, TextField } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { inventory, unwrap } from "../api/clients";
import { countryHeader, useCarClasses, useCarTypes, useLocations } from "../api/queries";
import type { Stock } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";

type Form = { carType: string; carClass: string; cars: string };

export function StockPage() {
  return <NeedsCountry>{(country) => <StockOfCountry country={country} />}</NeedsCountry>;
}

function StockOfCountry({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const locations = useLocations(country);
  const carTypes = useCarTypes();
  const carClasses = useCarClasses();
  const [chosen, setChosen] = useState("");
  const location = chosen || locations.data?.[0]?.id || "";
  const stock = useQuery({
    queryKey: ["stock", country, location],
    enabled: location !== "",
    queryFn: () =>
      unwrap(
        inventory.GET("/api/v1/admin/stock", {
          params: { query: { locationId: location }, ...countryHeader(country) },
        }),
      ),
  });
  const [editing, setEditing] = useState<{ value: Form; isNew: boolean } | null>(null);
  const save = useMutation({
    mutationFn: (form: Form) =>
      unwrap(
        inventory.PUT("/api/v1/admin/stock/{locationId}/{carType}/{carClass}", {
          params: {
            path: { locationId: location, carType: form.carType, carClass: form.carClass },
            ...countryHeader(country),
          },
          body: { cars: Number(form.cars) },
        }),
      ),
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: ["stock", country, location] });
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
        title="Stock"
        action={{
          label: "Add cars",
          disabled: location === "",
          onClick: () => open({ carType: "", carClass: "", cars: "" }, true),
        }}
      />
      <ErrorAlert error={locations.error ?? stock.error} />
      <TextField
        select
        id="stock-location"
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
      <ResponsiveList<Stock>
        items={stock.data ?? []}
        rowKey={(s) => `${s.carType}/${s.carClass}`}
        empty={stock.isLoading ? "Loading…" : "No cars at this location yet."}
        columns={[
          { label: "Car type", render: (s) => s.carType },
          { label: "Car class", render: (s) => s.carClass },
          { label: "Cars", render: (s) => s.cars },
        ]}
        actions={(s) => (
          <Button
            onClick={() =>
              open({ carType: s.carType, carClass: s.carClass, cars: String(s.cars) }, false)
            }
            aria-label={`Change ${s.carType} ${s.carClass}`}
            sx={{ minHeight: 44 }}
          >
            Change
          </Button>
        )}
      />
      {editing && (
        <FormDialog
          title={
            editing.isNew ? "Add cars" : `Change ${editing.value.carType} ${editing.value.carClass}`
          }
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing.value)}
        >
          <TextField
            select
            id="stock-car-type"
            label="Car type"
            value={editing.value.carType}
            disabled={!editing.isNew}
            onChange={(e) => change({ carType: e.target.value })}
            required
          >
            {(carTypes.data ?? []).map((t) => (
              <MenuItem key={t.code} value={t.code}>
                {t.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            id="stock-car-class"
            label="Car class"
            value={editing.value.carClass}
            disabled={!editing.isNew}
            onChange={(e) => change({ carClass: e.target.value })}
            required
          >
            {(carClasses.data ?? []).map((c) => (
              <MenuItem key={c.code} value={c.code}>
                {c.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Cars"
            type="number"
            inputMode="numeric"
            value={editing.value.cars}
            onChange={(e) => change({ cars: e.target.value })}
            required
          />
        </FormDialog>
      )}
    </>
  );
}
