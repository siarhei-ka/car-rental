import { Button, Chip, MenuItem, TextField } from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { reservations, unwrap } from "../api/clients";
import { countryHeader, useCarClasses, useCarTypes, useLocations } from "../api/queries";
import type { Reservation } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { NeedsCountry } from "../components/NeedsCountry";
import { PageHeader } from "../components/PageHeader";
import { ResponsiveList } from "../components/ResponsiveList";
import { formatInZone } from "../dates";
import { formatMoney } from "../money";

type Action = "pick-up" | "return" | "no-show";

const STATES: Record<Reservation["state"], string> = {
  CONFIRMED: "Confirmed",
  CANCELLED: "Cancelled",
  PICKED_UP: "Picked up",
  RETURNED: "Returned",
  NO_SHOW: "No-show",
};

/**
 * The reservations of a country, by pick-up location, ordered by start. Staff register the
 * pick-up, the return and a no-show here.
 */
export function ReservationsPage() {
  return <NeedsCountry>{(country) => <ReservationsOfCountry country={country} />}</NeedsCountry>;
}

function ReservationsOfCountry({ country }: { country: string }) {
  const queryClient = useQueryClient();
  const params = countryHeader(country);
  const locations = useLocations(country);
  const carTypes = useCarTypes();
  const carClasses = useCarClasses();
  const [chosen, setChosen] = useState("");
  const list = useQuery({
    queryKey: ["reservations", country, chosen],
    queryFn: () =>
      unwrap(
        reservations.GET("/api/v1/admin/reservations", {
          params: { query: chosen ? { locationId: chosen } : {}, ...params },
        }),
      ),
  });
  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: Action }) =>
      unwrap(
        reservations.POST(`/api/v1/admin/reservations/{id}/${action}`, {
          params: { path: { id }, ...params },
        }),
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["reservations", country] }),
  });

  const location = (id: string) => locations.data?.find((l) => l.id === id);
  const name = (codes: { code: string; name: string }[] | undefined, code: string) =>
    codes?.find((c) => c.code === code)?.name ?? code;

  return (
    <>
      <PageHeader title="Reservations" />
      <ErrorAlert error={locations.error ?? list.error ?? act.error} />
      <TextField
        select
        id="reservations-location"
        label="Pick-up location"
        value={chosen}
        onChange={(e) => setChosen(e.target.value)}
        slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
        sx={{ mb: 2, minWidth: { xs: "100%", sm: 280 } }}
      >
        <MenuItem value="">All locations</MenuItem>
        {(locations.data ?? []).map((l) => (
          <MenuItem key={l.id} value={l.id}>
            {l.name}
          </MenuItem>
        ))}
      </TextField>
      <ResponsiveList<Reservation>
        items={list.data ?? []}
        rowKey={(r) => r.id}
        empty={list.isLoading ? "Loading…" : "No reservations."}
        columns={[
          {
            label: "Start",
            render: (r) =>
              formatInZone(r.start, location(r.pickUpLocationId)?.timeZone) + ` · ${r.days} d`,
          },
          {
            label: "Car",
            render: (r) =>
              `${name(carTypes.data, r.carType)} · ${name(carClasses.data, r.carClass)}`,
          },
          {
            label: "Route",
            render: (r) => {
              const from = location(r.pickUpLocationId)?.name ?? r.pickUpLocationId;
              const to = location(r.returnLocationId)?.name ?? r.returnLocationId;
              return r.oneWay ? `${from} → ${to}` : from;
            },
          },
          { label: "Due at pick-up", render: (r) => (r.price ? formatMoney(r.price.total) : "—") },
          {
            label: "State",
            render: (r) => (
              <Chip
                label={STATES[r.state]}
                size="small"
                color={r.state === "CONFIRMED" ? "success" : "default"}
              />
            ),
          },
        ]}
        actions={(r) => <Actions reservation={r} busy={act.isPending} onAct={act.mutate} />}
      />
    </>
  );
}

/** What staff can register for a reservation in its state. */
function Actions({
  reservation,
  busy,
  onAct,
}: {
  reservation: Reservation;
  busy: boolean;
  onAct: (change: { id: string; action: Action }) => void;
}) {
  const button = (action: Action, label: string) => (
    <Button
      key={action}
      disabled={busy}
      onClick={() => onAct({ id: reservation.id, action })}
      aria-label={`${label}: reservation ${reservation.id}`}
      sx={{ minHeight: 44 }}
    >
      {label}
    </Button>
  );
  if (reservation.state === "CONFIRMED") {
    const started = new Date(reservation.start) <= new Date();
    return (
      <>
        {button("pick-up", "Pick up")}
        {started && button("no-show", "No-show")}
      </>
    );
  }
  if (reservation.state === "PICKED_UP") {
    return button("return", "Return");
  }
  return null;
}
