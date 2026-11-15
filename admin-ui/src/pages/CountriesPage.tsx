import { Button, FormControlLabel, Switch, TextField } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { administration, unwrap } from "../api/clients";
import type { Country } from "../api/types";
import { ActiveChip } from "../components/ActiveChip";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { PageHeader } from "../components/PageHeader";
import { useSession } from "../session";
import { ResponsiveList } from "../components/ResponsiveList";

const EMPTY: Country = { code: "", name: "", currency: "", active: true };

/** Countries are shared by all countries and will be limited to the super administrator. */
export function CountriesPage() {
  const queryClient = useQueryClient();
  const countries = useQuery({
    queryKey: ["countries"],
    queryFn: () => unwrap(administration.GET("/api/v1/admin/countries")),
  });
  const { superAdmin } = useSession();
  const [editing, setEditing] = useState<{ value: Country; isNew: boolean } | null>(null);
  const save = useMutation({
    mutationFn: (country: Country) =>
      unwrap(
        administration.PUT("/api/v1/admin/countries/{code}", {
          params: { path: { code: country.code } },
          body: { name: country.name, currency: country.currency, active: country.active },
        }),
      ),
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: ["countries"] });
    },
  });
  const open = (value: Country, isNew: boolean) => {
    save.reset();
    setEditing({ value, isNew });
  };
  const change = (patch: Partial<Country>) =>
    setEditing((current) => current && { ...current, value: { ...current.value, ...patch } });

  return (
    <>
      <PageHeader
        title="Countries"
        action={superAdmin ? { label: "Add country", onClick: () => open(EMPTY, true) } : undefined}
      />
      <ErrorAlert error={countries.error} />
      <ResponsiveList
        items={countries.data ?? []}
        rowKey={(c) => c.code}
        empty={countries.isLoading ? "Loading…" : "No countries yet."}
        columns={[
          { label: "Code", render: (c) => c.code },
          { label: "Name", render: (c) => c.name },
          { label: "Currency", render: (c) => c.currency },
          { label: "Status", render: (c) => <ActiveChip active={c.active} /> },
        ]}
        actions={
          superAdmin
            ? (c) => (
                <Button
                  onClick={() => open(c, false)}
                  aria-label={`Edit ${c.name}`}
                  sx={{ minHeight: 44 }}
                >
                  Edit
                </Button>
              )
            : undefined
        }
      />
      {editing && (
        <FormDialog
          title={editing.isNew ? "Add country" : `Edit ${editing.value.name}`}
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing.value)}
        >
          <TextField
            label="Code"
            helperText="Two lowercase letters, for example pl"
            value={editing.value.code}
            disabled={!editing.isNew}
            onChange={(e) => change({ code: e.target.value.trim().toLowerCase() })}
            required
          />
          <TextField
            label="Name"
            value={editing.value.name}
            onChange={(e) => change({ name: e.target.value })}
            required
          />
          <TextField
            label="Currency"
            helperText="ISO 4217 code, for example PLN"
            value={editing.value.currency}
            onChange={(e) => change({ currency: e.target.value.trim().toUpperCase() })}
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
