import { Alert, Button, Card, CardActions, CardContent, Stack, Typography } from "@mui/material";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link as RouterLink, useLocation, useNavigate, useSearchParams } from "react-router";
import { ApiError, reservationApi, unwrap } from "../api/clients";
import { useQuote } from "../api/queries";
import { newId } from "../ids";
import { CarPicture } from "../components/CarPicture";
import { PriceLines, type PriceView } from "../components/PriceLines";
import { fromQuery, toQuery, type SearchParams } from "../search";
import { profileUrl, signIn, useProfile, useSession } from "../session";

/**
 * The final price of the chosen car, insurance and extras, with every line, the discount and the
 * total due at pick-up. It needs a signed-in customer with a driver profile, because insurance is
 * priced for the driver. Reserving sends the total the customer sees; if the price has changed in
 * the meantime, nothing is reserved and the new price is shown to confirm again.
 */
export function PricePage() {
  const [query] = useSearchParams();
  const params = fromQuery(query);
  const session = useSession();
  const profile = useProfile(session.state === "customer");
  const ready = session.state === "customer" && profile.data != null;
  const quote = useQuote(params, ready);
  const location = useLocation();
  const here = location.pathname + location.search;

  if (!params || !params.carType || !params.carClass) {
    return <Alert severity="info">Choose a car from the search results first.</Alert>;
  }
  const selection = `/selection?${toQuery(params)}`;

  return (
    <Stack spacing={3} sx={{ maxWidth: 720, mx: "auto", width: "100%" }}>
      <Typography variant="h4" component="h1">
        Your price
      </Typography>
      {session.state === "signed-out" && (
        <Button
          variant="contained"
          size="large"
          onClick={() => signIn(here)}
          sx={{ minHeight: 48, alignSelf: "flex-start" }}
        >
          Sign in to see the price
        </Button>
      )}
      {session.state === "not-customer" && (
        <Alert severity="warning">
          The account {session.name} is not a customer account. Sign out and sign in as a customer
          to see the price.
        </Alert>
      )}
      {session.state === "customer" && profile.data === null && (
        <Button
          component={RouterLink}
          to={profileUrl(here)}
          variant="contained"
          size="large"
          sx={{ minHeight: 48, alignSelf: "flex-start" }}
        >
          Enter your driver profile
        </Button>
      )}
      {quote.isLoading && <Typography>Calculating your price…</Typography>}
      {quote.error && (
        <Problem error={quote.error} onRetry={() => void quote.refetch()} selection={selection} />
      )}
      {quote.data && (
        // A new quote starts a new reservation attempt, with its own idempotency key.
        <Reserve key={quote.dataUpdatedAt} params={params} quote={quote.data} />
      )}
      <Button component={RouterLink} to={selection} sx={{ alignSelf: "flex-start", minHeight: 44 }}>
        Change insurance or extras
      </Button>
    </Stack>
  );
}

/**
 * The price and the button that reserves at it. One idempotency key belongs to one price the
 * customer sees, so pressing the button twice never makes two reservations.
 */
function Reserve({ params, quote }: { params: SearchParams; quote: PriceView }) {
  const [shown, setShown] = useState<{ price: PriceView; key: string; changed: boolean }>(() => ({
    price: quote,
    key: newId(),
    changed: false,
  }));
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const reserve = useMutation({
    mutationFn: () =>
      unwrap(
        reservationApi.POST("/api/v1/reservations", {
          params: {
            header: { "X-Country": params.country, "Idempotency-Key": shown.key },
          },
          body: {
            pickUpLocationId: params.pickUp,
            returnLocationId: params.returnTo,
            start: params.start,
            days: params.days,
            carType: params.carType ?? "",
            carClass: params.carClass ?? "",
            insurance: params.insurance ?? "BASIC",
            extras: Object.entries(params.extras ?? {}).map(([code, quantity]) => ({
              code,
              quantity,
            })),
            expectedTotal: shown.price.total,
          },
        }),
      ),
    onSuccess: (reservation) => {
      void queryClient.invalidateQueries({ queryKey: ["reservations"] });
      navigate(`/reservations/${reservation.country}/${reservation.id}`, {
        state: { created: true },
      });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.type === "price-changed" && error.problem.price) {
        setShown({
          price: error.problem.price as PriceView,
          key: newId(),
          changed: true,
        });
      }
    },
  });
  const refused =
    reserve.error && !(reserve.error instanceof ApiError && reserve.error.type === "price-changed")
      ? reserve.error
      : null;

  return (
    <Card sx={{ overflow: "hidden" }}>
      <CarPicture
        carType={params.carType ?? ""}
        carClass={params.carClass ?? ""}
        alt=""
        sx={{ px: { xs: 4, sm: 12 }, pt: 3, pb: 1 }}
      />
      <CardContent>
        {shown.changed && (
          <Alert severity="warning" sx={{ mb: 2 }}>
            The price has changed since you saw it. Please check the new price and reserve again.
          </Alert>
        )}
        <PriceLines price={shown.price} />
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
          Prices include tax. You pay at pick-up.
        </Typography>
      </CardContent>
      <CardActions sx={{ px: 3, pb: 3, flexDirection: "column", alignItems: "stretch", gap: 1 }}>
        {refused && <Alert severity="error">{refused.message}</Alert>}
        <Button
          variant="contained"
          size="large"
          disabled={reserve.isPending}
          onClick={() => reserve.mutate()}
          sx={{ minHeight: 48 }}
        >
          {reserve.isPending ? "Reserving…" : "Reserve, pay at pick-up"}
        </Button>
      </CardActions>
    </Card>
  );
}

/** Why there is no price, and what the customer can do. */
function Problem({
  error,
  onRetry,
  selection,
}: {
  error: Error;
  onRetry: () => void;
  selection: string;
}) {
  if (error instanceof ApiError && error.status === 503) {
    return (
      <Alert
        severity="warning"
        action={
          <Button color="inherit" onClick={onRetry} sx={{ minHeight: 44 }}>
            Try again
          </Button>
        }
      >
        The price is not available right now. Please try again in a moment.
      </Alert>
    );
  }
  return (
    <Alert severity="error">
      {error.message} <RouterLink to={selection}>Change your choice</RouterLink>
    </Alert>
  );
}
