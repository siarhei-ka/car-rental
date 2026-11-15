import { Button, FormControlLabel, Stack, Switch, TextField } from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { administration, unwrap } from "../api/clients";
import { countryHeader, useLocations } from "../api/queries";
import type { Location } from "../api/types";
import { ActiveChip } from "../components/ActiveChip";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";

type Form = Omit<Location, "latitude" | "longitude"> & { latitude: string; longitude: string };

export function LocationsPage() {
  return <NeedsCountry>{(country) => <Locations country={country} />}</NeedsCountry>;
}

function Locations({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const locations = useLocations(country);
  const [editing, setEditing] = useState<{ value: Form; isNew: boolean } | null>(null);
  const save = useMutation({
    mutationFn: (form: Form) =>
      unwrap(
        administration.PUT("/api/v1/admin/locations/{id}", {
          params: { path: { id: form.id }, ...countryHeader(country) },
          body: {
            name: form.name,
            city: form.city,
            latitude: Number(form.latitude),
            longitude: Number(form.longitude),
            timeZone: form.timeZone,
            active: form.active,
          },
        }),
      ),
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: ["locations", country] });
    },
  });
  const open = (value: Form, isNew: boolean) => {
    save.reset();
    setEditing({ value, isNew });
  };
  const change = (patch: Partial<Form>) =>
    setEditing((current) => current && { ...current, value: { ...current.value, ...patch } });
  const empty: Form = {
    id: "",
    country,
    name: "",
    city: "",
    latitude: "",
    longitude: "",
    timeZone: "",
    active: true,
  };

  return (
    <>
      <PageHeader
        title="Locations"
        action={{ label: "Add location", onClick: () => open(empty, true) }}
      />
      <ErrorAlert error={locations.error} />
      <ResponsiveList
        items={locations.data ?? []}
        rowKey={(l) => l.id}
        empty={locations.isLoading ? "Loading…" : "No locations in this country yet."}
        columns={[
          { label: "ID", render: (l) => l.id },
          { label: "Name", render: (l) => l.name },
          { label: "City", render: (l) => l.city },
          { label: "Time zone", render: (l) => l.timeZone },
          { label: "Status", render: (l) => <ActiveChip active={l.active} /> },
        ]}
        actions={(l) => (
          <Button
            onClick={() =>
              open({ ...l, latitude: String(l.latitude), longitude: String(l.longitude) }, false)
            }
            aria-label={`Edit ${l.name}`}
            sx={{ minHeight: 44 }}
          >
            Edit
          </Button>
        )}
      />
      {editing && (
        <FormDialog
          title={editing.isNew ? "Add location" : `Edit ${editing.value.name}`}
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing.value)}
        >
          <TextField
            label="ID"
            helperText="For example waw-airport"
            value={editing.value.id}
            disabled={!editing.isNew}
            onChange={(e) => change({ id: e.target.value.trim() })}
            required
          />
          <TextField
            label="Name"
            value={editing.value.name}
            onChange={(e) => change({ name: e.target.value })}
            required
          />
          <TextField
            label="City"
            value={editing.value.city}
            onChange={(e) => change({ city: e.target.value })}
            required
          />
          <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
            <TextField
              label="Latitude"
              inputMode="decimal"
              value={editing.value.latitude}
              onChange={(e) => change({ latitude: e.target.value })}
              required
              fullWidth
            />
            <TextField
              label="Longitude"
              inputMode="decimal"
              value={editing.value.longitude}
              onChange={(e) => change({ longitude: e.target.value })}
              required
              fullWidth
            />
          </Stack>
          <TextField
            label="Time zone"
            helperText="A region, for example Europe/Warsaw"
            value={editing.value.timeZone}
            onChange={(e) => change({ timeZone: e.target.value.trim() })}
            required
          />
          <FormControlLabel
            control={
              <Switch
                checked={editing.value.active}
                onChange={(e) => change({ active: e.target.checked })}
              />
            }
            label="Active"
          />
        </FormDialog>
      )}
    </>
  );
}
