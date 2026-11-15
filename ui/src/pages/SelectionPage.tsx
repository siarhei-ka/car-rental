import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import {
  Alert,
  alpha,
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Divider,
  FormControl,
  FormControlLabel,
  FormLabel,
  Grid,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Link as RouterLink, useLocation, useSearchParams } from "react-router";
import { useExtras, useInsuranceOptions, useOneWayFee, useSearch } from "../api/queries";
import type { Extra } from "../api/types";
import { CarPicture } from "../components/CarPicture";
import { ErrorAlert } from "../components/ErrorAlert";
import { TripSummary } from "../components/TripSummary";
import { formatMoney } from "../money";
import { fromQuery, toQuery, type SearchParams } from "../search";
import { profileUrl, signIn, useProfile, useSession } from "../session";

/**
 * The chosen car. The search is repeated, so that the page shows whether the car is still free.
 * The customer chooses the insurance and the extras here, at list prices, without signing in. The
 * choice is kept in the address bar, so it survives the sign-in. The final price needs a
 * signed-in customer with a driver profile.
 */
export function SelectionPage() {
  const [query, setQuery] = useSearchParams();
  const params = fromQuery(query);
  const result = useSearch(params && { ...params, insurance: undefined, extras: undefined });

  if (!params || !params.carType || !params.carClass) {
    return <Alert severity="info">Choose a car from the search results first.</Alert>;
  }
  const back = `/?${toQuery({
    ...params,
    carType: undefined,
    carClass: undefined,
    insurance: undefined,
    extras: undefined,
  })}`;
  const offer = result.data?.offers.find(
    (o) => o.carType === params.carType && o.carClass === params.carClass,
  );
  const choose = (patch: Partial<SearchParams>) =>
    setQuery(toQuery({ ...params, ...patch }), { replace: true });

  return (
    <Stack spacing={3}>
      <Button
        component={RouterLink}
        to={back}
        startIcon={<ArrowBackIcon />}
        sx={{ alignSelf: "flex-start", minHeight: 44 }}
      >
        Back to the results
      </Button>
      <Typography variant="h4" component="h1">
        Your choice
      </Typography>
      <ErrorAlert error={result.error} />
      {result.isLoading && <Typography>Checking that the car is still free…</Typography>}
      {result.data && !offer && (
        <Alert severity="warning">
          This car is no longer free for your dates. Please choose another one.
        </Alert>
      )}
      {result.data && offer && (
        <Grid container spacing={3} sx={{ alignItems: "flex-start" }}>
          <Grid size={{ xs: 12, md: 5 }} sx={{ position: { md: "sticky" }, top: { md: 88 } }}>
            <Card sx={{ overflow: "hidden" }}>
              <CarPicture
                carType={offer.carType}
                carClass={offer.carClass}
                alt={`${offer.carTypeName}, ${offer.carClassName}`}
                sx={{ px: 4, pt: 4, pb: 2 }}
              />
              <CardContent>
                <Typography variant="h5" component="h2">
                  {offer.carTypeName} · {offer.carClassName}
                </Typography>
                <Box sx={{ mt: 1.5 }}>
                  <TripSummary result={result.data} />
                </Box>
                <Divider sx={{ my: 2 }} />
                <Typography>
                  Car rental: {formatMoney(offer.dailyRate)} per day, list price.
                </Typography>
                <OneWayFee params={params} />
                <Typography sx={{ mt: 1 }} color="text.secondary">
                  Payment: at pick-up.
                </Typography>
              </CardContent>
            </Card>
          </Grid>
          <Grid size={{ xs: 12, md: 7 }}>
            <Card>
              <CardContent>
                <Insurance
                  country={params.country}
                  chosen={params.insurance}
                  onChoose={(insurance) => choose({ insurance })}
                />
                <Extras
                  country={params.country}
                  chosen={params.extras ?? {}}
                  onChoose={(extras) => choose({ extras })}
                />
              </CardContent>
              <CardActions
                sx={{ px: 3, pb: 3, flexDirection: "column", alignItems: "stretch", gap: 1 }}
              >
                <NextStep params={params} />
              </CardActions>
            </Card>
          </Grid>
        </Grid>
      )}
    </Stack>
  );
}

/** What the customer can do next: sign in, enter the driver profile, then see the price. */
function NextStep({ params }: { params: SearchParams }) {
  const session = useSession();
  const profile = useProfile(session.state === "customer");
  const options = useInsuranceOptions(params.country);
  const location = useLocation();
  const here = location.pathname + location.search;

  if (session.state === "loading" || (session.state === "customer" && profile.isPending)) {
    return null;
  }
  if (session.state === "signed-out") {
    return (
      <Button variant="contained" size="large" onClick={() => signIn(here)} sx={{ minHeight: 48 }}>
        Sign in to see the price and reserve
      </Button>
    );
  }
  if (session.state === "not-customer") {
    return (
      <Alert severity="warning">
        The account {session.name} is not a customer account. Sign out and sign in as a customer to
        reserve.
      </Alert>
    );
  }
  if (!profile.data) {
    return (
      <Button
        component={RouterLink}
        to={profileUrl(here)}
        variant="contained"
        size="large"
        sx={{ minHeight: 48 }}
      >
        Enter your driver profile
      </Button>
    );
  }
  // The price page is asked for the option on screen, also when it is the preselected one.
  const insurance = params.insurance ?? options.data?.find((o) => o.preselected)?.code;
  return (
    <Button
      component={RouterLink}
      to={`/price?${toQuery({ ...params, insurance })}`}
      variant="contained"
      size="large"
      sx={{ minHeight: 48 }}
    >
      See the price
    </Button>
  );
}

/**
 * The insurance options with their base price per day. The price for the driver, which depends on
 * the driver's age and licence country, comes with the final price.
 */
function Insurance({
  country,
  chosen,
  onChoose,
}: {
  country: string;
  chosen: string | undefined;
  onChoose: (code: string) => void;
}) {
  const options = useInsuranceOptions(country);

  if (options.isError) {
    return (
      <Typography sx={{ mt: 2 }} color="text.secondary">
        Insurance prices are not available right now.
      </Typography>
    );
  }
  if (!options.data) {
    return null;
  }
  const value = chosen ?? options.data.find((o) => o.preselected)?.code ?? "";
  return (
    <FormControl component="fieldset" sx={{ display: "block" }}>
      <FormLabel
        component="legend"
        sx={{
          typography: "h6",
          color: "text.primary",
          mb: 1.5,
          "&.Mui-focused": { color: "text.primary" },
        }}
      >
        Insurance
      </FormLabel>
      <RadioGroup value={value} onChange={(e) => onChoose(e.target.value)} sx={{ gap: 1 }}>
        {options.data.map((option) => (
          <FormControlLabel
            key={option.code}
            value={option.code}
            control={<Radio />}
            label={`${option.name}: from ${formatMoney(option.pricePerDay)} per day`}
            sx={(theme) => ({
              m: 0,
              minHeight: 52,
              pr: 2,
              border: 1,
              borderRadius: "12px",
              borderColor: option.code === value ? "primary.main" : "divider",
              bgcolor:
                option.code === value ? alpha(theme.palette.primary.main, 0.06) : "transparent",
              transition: "border-color 120ms, background-color 120ms",
              "& .MuiFormControlLabel-label": { fontWeight: option.code === value ? 600 : 400 },
            })}
          />
        ))}
      </RadioGroup>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
        The price for you depends on your age and where your licence was issued.
      </Typography>
    </FormControl>
  );
}

/** The extras on offer, each with its list price and how many to take. */
function Extras({
  country,
  chosen,
  onChoose,
}: {
  country: string;
  chosen: Record<string, number>;
  onChoose: (extras: Record<string, number>) => void;
}) {
  const extras = useExtras(country);

  if (extras.isError) {
    return (
      <Typography sx={{ mt: 2 }} color="text.secondary">
        Extras are not available right now.
      </Typography>
    );
  }
  if (!extras.data || extras.data.length === 0) {
    return null;
  }
  return (
    <Stack component="section" aria-label="Extras" spacing={1} sx={{ mt: 4 }}>
      <Typography variant="h6" component="h3" sx={{ mb: 0.5 }}>
        Extras
      </Typography>
      {extras.data.map((extra) => (
        <ExtraChoice
          key={extra.code}
          extra={extra}
          quantity={chosen[extra.code] ?? 0}
          onChange={(quantity) => onChoose({ ...chosen, [extra.code]: quantity })}
        />
      ))}
    </Stack>
  );
}

function ExtraChoice({
  extra,
  quantity,
  onChange,
}: {
  extra: Extra;
  quantity: number;
  onChange: (quantity: number) => void;
}) {
  const charge = extra.charge === "PER_DAY" ? "per day" : "per rental";
  return (
    <Stack
      direction={{ xs: "column", sm: "row" }}
      spacing={1}
      sx={{
        alignItems: { sm: "center" },
        justifyContent: "space-between",
        border: 1,
        borderColor: quantity > 0 ? "primary.main" : "divider",
        borderRadius: "12px",
        px: 2,
        py: 1.25,
      }}
    >
      <Typography>
        {extra.name}: {formatMoney(extra.price)} {charge}
      </Typography>
      <TextField
        select
        size="small"
        value={String(quantity)}
        onChange={(e) => onChange(Number(e.target.value))}
        slotProps={{
          select: { SelectDisplayProps: { "aria-label": `How many: ${extra.name}` } },
        }}
        sx={{ minWidth: 120 }}
      >
        {Array.from({ length: extra.maxQuantity + 1 }, (_, n) => (
          <MenuItem key={n} value={String(n)}>
            {n === 0 ? "None" : n}
          </MenuItem>
        ))}
      </TextField>
    </Stack>
  );
}

/** The fee for returning the car to another location, which the customer cannot remove. */
function OneWayFee({ params }: { params: SearchParams }) {
  const oneWay = params.returnTo !== params.pickUp;
  const fee = useOneWayFee(params.country, oneWay);
  if (!oneWay || !fee.data) {
    return null;
  }
  return (
    <Typography sx={{ mt: 1 }}>
      One-way fee: {formatMoney(fee.data)}, because the car is returned to another location.
    </Typography>
  );
}
