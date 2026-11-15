import { Button, MenuItem, Stack, TextField } from "@mui/material";
import { DateTimePicker } from "@mui/x-date-pickers/DateTimePicker";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { Dayjs } from "dayjs";
import { useState } from "react";
import { inventory, unwrap } from "../api/clients";
import { countryHeader, useCarClasses, useCarTypes, useLocations } from "../api/queries";
import type { Transfer } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";
import { dayjs, formatInZone } from "../dates";
import { newId } from "../ids";

type Form = {
  from: string;
  to: string;
  carType: string;
  carClass: string;
  cars: string;
  departure: Dayjs | null;
  arrival: Dayjs | null;
};

export function TransfersPage() {
  return <NeedsCountry>{(country) => <Transfers country={country} />}</NeedsCountry>;
}

function Transfers({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const locations = useLocations(country);
  const carTypes = useCarTypes();
  const carClasses = useCarClasses();
  const zoneOf = (id: string) => locations.data?.find((l) => l.id === id)?.timeZone;
  const nameOf = (id: string) => locations.data?.find((l) => l.id === id)?.name ?? id;
  const transfers = useQuery({
    queryKey: ["transfers", country],
    queryFn: () =>
      unwrap(inventory.GET("/api/v1/admin/transfers", { params: countryHeader(country) })),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["transfers", country] });
  const arrive = useMutation({
    mutationFn: (id: string) =>
      unwrap(
        inventory.POST("/api/v1/admin/transfers/{transferId}/arrival", {
          params: { path: { transferId: id }, ...countryHeader(country) },
        }),
      ),
    onSuccess: refresh,
  });
  const [form, setForm] = useState<Form | null>(null);
  const send = useMutation({
    mutationFn: (f: Form) =>
      unwrap(
        inventory.PUT("/api/v1/admin/transfers/{transferId}", {
          params: { path: { transferId: newId() }, ...countryHeader(country) },
          body: {
            locationId: f.from,
            carType: f.carType,
            carClass: f.carClass,
            cars: Number(f.cars),
            departure: f.departure?.toISOString() ?? "",
            targetLocationId: f.to,
            plannedArrival: f.arrival?.toISOString() ?? "",
          },
        }),
      ),
    onSuccess: () => {
      setForm(null);
      return refresh();
    },
  });
  const change = (patch: Partial<Form>) =>
    setForm((current) => current && { ...current, ...patch });
  const openForm = () => {
    send.reset();
    setForm({
      from: "",
      to: "",
      carType: "",
      carClass: "",
      cars: "1",
      departure: null,
      arrival: null,
    });
  };

  return (
    <>
      <PageHeader title="Transfers" action={{ label: "Send cars", onClick: openForm }} />
      <ErrorAlert error={transfers.error ?? arrive.error} />
      <ResponsiveList<Transfer>
        items={transfers.data ?? []}
        rowKey={(t) => t.id}
        empty={transfers.isLoading ? "Loading…" : "No transfers on the way."}
        columns={[
          { label: "From", render: (t) => nameOf(t.locationId) },
          { label: "To", render: (t) => nameOf(t.targetLocationId) },
          { label: "Cars", render: (t) => `${t.cars} × ${t.carType} ${t.carClass}` },
          { label: "Departure", render: (t) => formatInZone(t.departure, zoneOf(t.locationId)) },
          {
            label: "Planned arrival",
            render: (t) => formatInZone(t.plannedArrival, zoneOf(t.targetLocationId)),
          },
        ]}
        actions={(t) => (
          <Button
            variant="outlined"
            onClick={() => arrive.mutate(t.id)}
            disabled={arrive.isPending}
            sx={{ minHeight: 44 }}
          >
            Register arrival
          </Button>
        )}
      />
      {form && (
        <FormDialog
          title="Send cars to another location"
          open
          saving={send.isPending}
          error={send.error}
          onClose={() => setForm(null)}
          onSubmit={() => send.mutate(form)}
        >
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              select
              fullWidth
              id="transfer-from"
              label="From"
              value={form.from}
              onChange={(e) => change({ from: e.target.value })}
              required
            >
              {(locations.data ?? []).map((l) => (
                <MenuItem key={l.id} value={l.id}>
                  {l.name}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              fullWidth
              id="transfer-to"
              label="To"
              value={form.to}
              onChange={(e) => change({ to: e.target.value })}
              required
            >
              {(locations.data ?? [])
                .filter((l) => l.id !== form.from)
                .map((l) => (
                  <MenuItem key={l.id} value={l.id}>
                    {l.name}
                  </MenuItem>
                ))}
            </TextField>
          </Stack>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              select
              fullWidth
              id="transfer-car-type"
              label="Car type"
              value={form.carType}
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
              fullWidth
              id="transfer-car-class"
              label="Car class"
              value={form.carClass}
              onChange={(e) => change({ carClass: e.target.value })}
              required
            >
              {(carClasses.data ?? []).map((c) => (
                <MenuItem key={c.code} value={c.code}>
                  {c.name}
                </MenuItem>
              ))}
            </TextField>
          </Stack>
          <TextField
            label="Cars"
            type="number"
            inputMode="numeric"
            value={form.cars}
            onChange={(e) => change({ cars: e.target.value })}
            required
          />
          <DateTimePicker
            label="Departure"
            value={form.departure}
            onChange={(value) => change({ departure: value })}
            timezone={zoneOf(form.from) ?? "default"}
            ampm={false}
            slotProps={{
              openPickerButton: { sx: { minWidth: 44, minHeight: 44 } },
              textField: {
                required: true,
                helperText: "Local time of the departure location",
                slotProps: { inputLabel: { shrink: true } },
              },
            }}
          />
          <DateTimePicker
            label="Planned arrival"
            value={form.arrival}
            onChange={(value) => change({ arrival: value })}
            timezone={zoneOf(form.to) ?? "default"}
            ampm={false}
            minDateTime={form.departure ?? dayjs()}
            slotProps={{
              openPickerButton: { sx: { minWidth: 44, minHeight: 44 } },
              textField: {
                required: true,
                helperText: "Local time of the target location",
                slotProps: { inputLabel: { shrink: true } },
              },
            }}
          />
        </FormDialog>
      )}
    </>
  );
}
