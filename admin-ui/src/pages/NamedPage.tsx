import { Button, FormControlLabel, Switch, TextField } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { administration, unwrap } from "../api/clients";
import type { Named } from "../api/types";
import { ActiveChip } from "../components/ActiveChip";
import { ErrorAlert } from "../components/ErrorAlert";
import { FormDialog } from "../components/FormDialog";
import { PageHeader } from "../components/PageHeader";
import { useSession } from "../session";
import { ResponsiveList } from "../components/ResponsiveList";

type Kind = "car-types" | "car-classes";

const TEXT: Record<Kind, { title: string; one: string; example: string }> = {
  "car-types": { title: "Car types", one: "car type", example: "SEDAN" },
  "car-classes": { title: "Car classes", one: "car class", example: "ECONOMY" },
};

const EMPTY: Named = { code: "", name: "", active: true };

/** Car types or car classes: reference data with a code, a name and an active flag. */
export function NamedPage({ kind }: { kind: Kind }) {
  const text = TEXT[kind];
  const queryClient = useQueryClient();
  const items = useQuery({
    queryKey: [kind],
    queryFn: () =>
      kind === "car-types"
        ? unwrap(administration.GET("/api/v1/admin/car-types"))
        : unwrap(administration.GET("/api/v1/admin/car-classes")),
  });
  const { superAdmin } = useSession();
  const [editing, setEditing] = useState<{ value: Named; isNew: boolean } | null>(null);
  const save = useMutation({
    mutationFn: (item: Named) => {
      const options = {
        params: { path: { code: item.code } },
        body: { name: item.name, active: item.active },
      };
      return kind === "car-types"
        ? unwrap(administration.PUT("/api/v1/admin/car-types/{code}", options))
        : unwrap(administration.PUT("/api/v1/admin/car-classes/{code}", options));
    },
    onSuccess: () => {
      setEditing(null);
      return queryClient.invalidateQueries({ queryKey: [kind] });
    },
  });
  const open = (value: Named, isNew: boolean) => {
    save.reset();
    setEditing({ value, isNew });
  };
  const change = (patch: Partial<Named>) =>
    setEditing((current) => current && { ...current, value: { ...current.value, ...patch } });

  return (
    <>
      <PageHeader
        title={text.title}
        action={
          superAdmin ? { label: `Add ${text.one}`, onClick: () => open(EMPTY, true) } : undefined
        }
      />
      <ErrorAlert error={items.error} />
      <ResponsiveList
        items={items.data ?? []}
        rowKey={(item) => item.code}
        empty={items.isLoading ? "Loading…" : "Nothing yet."}
        columns={[
          { label: "Code", render: (item) => item.code },
          { label: "Name", render: (item) => item.name },
          { label: "Status", render: (item) => <ActiveChip active={item.active} /> },
        ]}
        actions={
          superAdmin
            ? (item) => (
                <Button
                  onClick={() => open(item, false)}
                  aria-label={`Edit ${item.name}`}
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
          title={editing.isNew ? `Add ${text.one}` : `Edit ${editing.value.name}`}
          open
          saving={save.isPending}
          error={save.error}
          onClose={() => setEditing(null)}
          onSubmit={() => save.mutate(editing.value)}
        >
          <TextField
            label="Code"
            helperText={`Uppercase letters, digits and underscores, for example ${text.example}`}
            value={editing.value.code}
            disabled={!editing.isNew}
            onChange={(e) => change({ code: e.target.value.trim().toUpperCase() })}
            required
          />
          <TextField
            label="Name"
            value={editing.value.name}
            onChange={(e) => change({ name: e.target.value })}
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
