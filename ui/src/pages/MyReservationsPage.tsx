import {
  Alert,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Stack,
  Typography,
} from "@mui/material";
import { Link as RouterLink } from "react-router";
import { useMyReservations } from "../api/queries";
import { CarPicture } from "../components/CarPicture";
import { ErrorAlert } from "../components/ErrorAlert";
import { formatMoney } from "../money";
import { STATES } from "../reservations";
import { signIn, useSession } from "../session";
import { Trip, useNames } from "./ReservationPage";

/** The signed-in customer's reservations, latest start first. */
export function MyReservationsPage() {
  const session = useSession();
  const reservations = useMyReservations(session.state === "customer");
  const names = useNames();

  if (session.state === "signed-out") {
    return (
      <Button variant="contained" onClick={() => signIn("/reservations")} sx={{ minHeight: 48 }}>
        Sign in to see your reservations
      </Button>
    );
  }
  if (session.state === "not-customer") {
    return <Alert severity="warning">Only customer accounts have reservations.</Alert>;
  }
  return (
    <Stack spacing={2.5} sx={{ maxWidth: 880, mx: "auto", width: "100%" }}>
      <Typography variant="h4" component="h1">
        Your reservations
      </Typography>
      <ErrorAlert error={reservations.error} />
      {reservations.isLoading && <Typography>Loading your reservations…</Typography>}
      {reservations.data?.length === 0 && (
        <Typography>
          You have no reservations yet. <RouterLink to="/">Find a car</RouterLink>
        </Typography>
      )}
      {reservations.data?.map((reservation) => (
        <Card
          key={reservation.id}
          sx={{ display: "flex", flexDirection: { xs: "column", sm: "row" }, overflow: "hidden" }}
        >
          <CarPicture
            carType={reservation.carType}
            carClass={reservation.carClass}
            alt=""
            sx={{ width: { sm: 220 }, flexShrink: 0, px: 2, py: { xs: 2, sm: 0 } }}
          />
          <Box sx={{ flexGrow: 1, display: "flex", flexDirection: "column" }}>
            <CardContent sx={{ flexGrow: 1 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: "center", flexWrap: "wrap" }}>
                <Typography variant="h6" component="h2">
                  {names.car(reservation)}
                </Typography>
                <Chip
                  label={STATES[reservation.state]}
                  color={reservation.state === "CONFIRMED" ? "success" : "default"}
                  size="small"
                />
              </Stack>
              <Trip reservation={reservation} />
              {reservation.price && (
                <Typography sx={{ mt: 1 }}>
                  {formatMoney(reservation.price.total)}, due at pick-up
                </Typography>
              )}
            </CardContent>
            <CardActions sx={{ px: 3, pb: 2 }}>
              <Button
                variant="outlined"
                component={RouterLink}
                to={`/reservations/${reservation.country}/${reservation.id}`}
                aria-label={`View reservation of ${names.car(reservation)} from ${reservation.start}`}
                sx={{ minHeight: 44 }}
              >
                View
              </Button>
            </CardActions>
          </Box>
        </Card>
      ))}
    </Stack>
  );
}
