import {
  Alert,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link as RouterLink, useLocation, useParams } from "react-router";
import { reservationApi, unwrap } from "../api/clients";
import { useCarClasses, useCarTypes, useLocations, useReservation } from "../api/queries";
import type { Reservation } from "../api/types";
import { CarPicture } from "../components/CarPicture";
import { ErrorAlert } from "../components/ErrorAlert";
import { PriceLines } from "../components/PriceLines";
import { formatInZone } from "../dates";
import { formatMoney } from "../money";
import { canCancel, STATES } from "../reservations";
import { signIn, useSession } from "../session";

/**
 * One reservation of the signed-in customer: where and when, the price it was made with, and the
 * amount due at pick-up. A confirmed reservation can be cancelled before its start.
 */
export function ReservationPage() {
  const { country = "", id = "" } = useParams();
  const session = useSession();
  const reservation = useReservation(country, id, session.state === "customer");
  const location = useLocation();
  const created = (location.state as { created?: boolean } | null)?.created === true;

  if (session.state === "signed-out") {
    return (
      <Button variant="contained" onClick={() => signIn(location.pathname)} sx={{ minHeight: 48 }}>
        Sign in to see your reservation
      </Button>
    );
  }
  return (
    <Stack spacing={3} sx={{ maxWidth: 720, mx: "auto", width: "100%" }}>
      <Typography variant="h4" component="h1">
        {created ? "Your car is reserved" : "Your reservation"}
      </Typography>
      {created && reservation.data?.price && (
        <Alert severity="success">
          Pay {formatMoney(reservation.data.price.total)} when you pick up the car.
        </Alert>
      )}
      <ErrorAlert error={reservation.error} />
      {reservation.isLoading && <Typography>Loading your reservation…</Typography>}
      {reservation.data && <Details reservation={reservation.data} />}
      <Button
        component={RouterLink}
        to="/reservations"
        sx={{ alignSelf: "flex-start", minHeight: 44 }}
      >
        All your reservations
      </Button>
    </Stack>
  );
}

function Details({ reservation }: { reservation: Reservation }) {
  const queryClient = useQueryClient();
  const names = useNames();
  const cancel = useMutation({
    mutationFn: () =>
      unwrap(
        reservationApi.POST("/api/v1/reservations/{id}/cancel", {
          params: { header: { "X-Country": reservation.country }, path: { id: reservation.id } },
        }),
      ),
    onSuccess: (changed) => {
      queryClient.setQueryData(["reservation", reservation.country, reservation.id], changed);
      return queryClient.invalidateQueries({ queryKey: ["reservations"] });
    },
  });

  return (
    <Card sx={{ overflow: "hidden" }}>
      <CarPicture
        carType={reservation.carType}
        carClass={reservation.carClass}
        alt=""
        sx={{ px: { xs: 4, sm: 12 }, pt: 3, pb: 1 }}
      />
      <CardContent>
        <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
          <Typography variant="h5" component="h2">
            {names.car(reservation)}
          </Typography>
          <Chip
            label={STATES[reservation.state]}
            color={reservation.state === "CONFIRMED" ? "success" : "default"}
            size="small"
          />
        </Stack>
        <Trip reservation={reservation} />
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          Reservation {reservation.id}
        </Typography>
        {reservation.price && (
          <Stack sx={{ mt: 2 }}>
            <PriceLines price={reservation.price} />
          </Stack>
        )}
        <Typography sx={{ mt: 2 }}>Payment: at pick-up.</Typography>
      </CardContent>
      {canCancel(reservation) && (
        <CardActions sx={{ px: 3, pb: 3, flexDirection: "column", alignItems: "stretch", gap: 1 }}>
          <ErrorAlert error={cancel.error} />
          <Button
            variant="outlined"
            color="error"
            disabled={cancel.isPending}
            onClick={() => cancel.mutate()}
            sx={{ minHeight: 48 }}
          >
            Cancel this reservation
          </Button>
        </CardActions>
      )}
    </Card>
  );
}

/** Where and when, in the local time of each location. */
export function Trip({ reservation }: { reservation: Reservation }) {
  const names = useNames();
  const from = names.location(reservation.pickUpLocationId);
  const to = names.location(reservation.returnLocationId);
  return (
    <>
      <Typography sx={{ mt: 1 }}>
        {from.name}
        {reservation.oneWay && ` → ${to.name}`}
      </Typography>
      <Typography color="text.secondary">
        {formatInZone(reservation.start, from.timeZone)} –{" "}
        {formatInZone(reservation.end, to.timeZone)} · {reservation.days}{" "}
        {reservation.days === 1 ? "day" : "days"} · local times
      </Typography>
    </>
  );
}

/** Names of locations, car types and classes, falling back to their codes while loading. */
export function useNames() {
  const locations = useLocations();
  const carTypes = useCarTypes();
  const carClasses = useCarClasses();
  return {
    location: (id: string) => {
      const found = locations.data?.find((l) => l.id === id);
      return { name: found?.name ?? id, timeZone: found?.timeZone };
    },
    car: (reservation: Reservation) =>
      `${carTypes.data?.find((t) => t.code === reservation.carType)?.name ?? reservation.carType} · ${
        carClasses.data?.find((c) => c.code === reservation.carClass)?.name ?? reservation.carClass
      }`,
  };
}
