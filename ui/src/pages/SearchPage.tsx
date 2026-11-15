import AddIcon from "@mui/icons-material/Add";
import EventAvailableIcon from "@mui/icons-material/EventAvailable";
import KeyboardReturnIcon from "@mui/icons-material/KeyboardReturn";
import PlaceIcon from "@mui/icons-material/Place";
import RemoveIcon from "@mui/icons-material/Remove";
import SearchIcon from "@mui/icons-material/Search";
import {
  Box,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Grid,
  IconButton,
  InputAdornment,
  ListSubheader,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { useCarClasses, useCarTypes, useCountries, useLocations, useSearch } from "../api/queries";
import type { LocationInfo, Offer } from "../api/types";
import { ErrorAlert } from "../components/ErrorAlert";
import { CarPicture } from "../components/CarPicture";
import { PickUpPicker } from "../components/PickUpPicker";
import { TripSummary } from "../components/TripSummary";
import { atWallClock, wallClock } from "../dates";
import { formatMoney } from "../money";
import { fromQuery, toQuery, type SearchParams } from "../search";

const MAX_DAYS = 30;

type Form = {
  pickUp: string;
  returnTo: string;
  /** The pick-up day and time on the clocks of the pick-up location. */
  date: string | null;
  time: string;
  days: string;
  carType: string;
  carClass: string;
};

/** The search form, and the offers of the current search below it. */
export function SearchPage() {
  const [query, setQuery] = useSearchParams();
  const current = fromQuery(query);
  const result = useSearch(current);
  const locations = useLocations();
  const navigate = useNavigate();
  // A search in the address bar is shown in the local time of its location, once that is known.
  const formReady = !current || locations.data != null || locations.isError;
  const cities = new Set((locations.data ?? []).map((l) => `${l.country}/${l.city}`)).size;

  return (
    <Stack spacing={4}>
      <Box>
        <Hero cities={cities} />
        <Box sx={{ position: "relative", mt: { xs: -3, md: -8 }, mx: { xs: 1, sm: 3, md: 5 } }}>
          {formReady && (
            <SearchForm initial={current} onSearch={(params) => setQuery(toQuery(params))} />
          )}
        </Box>
      </Box>
      {current && (
        <Box component="section" aria-label="Results">
          <ErrorAlert error={result.error} />
          {result.isLoading && <Typography>Searching…</Typography>}
          {result.data && (
            <Stack spacing={2.5}>
              <Stack
                direction={{ xs: "column", md: "row" }}
                spacing={1}
                sx={{ justifyContent: "space-between", alignItems: { md: "flex-end" } }}
              >
                <Typography variant="h5" component="p">
                  {result.data.offers.length === 0
                    ? "No cars"
                    : `${result.data.offers.length} ${result.data.offers.length === 1 ? "kind of car" : "kinds of cars"} for your trip`}
                </Typography>
                <Box>
                  <TripSummary result={result.data} />
                </Box>
              </Stack>
              {result.data.offers.length === 0 ? (
                <Typography>
                  No cars are free for this search. Try other dates or a location.
                </Typography>
              ) : (
                <Grid container spacing={2.5}>
                  {result.data.offers.map((offer) => (
                    <Grid
                      key={`${offer.carType}/${offer.carClass}`}
                      size={{ xs: 12, sm: 6, md: 4 }}
                    >
                      <OfferCard
                        offer={offer}
                        onChoose={() =>
                          navigate(
                            `/selection?${toQuery({
                              ...current,
                              carType: offer.carType,
                              carClass: offer.carClass,
                            })}`,
                          )
                        }
                      />
                    </Grid>
                  ))}
                </Grid>
              )}
            </Stack>
          )}
        </Box>
      )}
    </Stack>
  );
}

function Hero({ cities }: { cities: number }) {
  return (
    <Box
      sx={{
        position: "relative",
        overflow: "hidden",
        borderRadius: { xs: "20px", md: "28px" },
        color: "common.white",
        px: { xs: 3, md: 6 },
        pt: { xs: 4, md: 6 },
        pb: { xs: 6, md: 12 },
        background:
          "radial-gradient(120% 140% at 100% 0%, rgba(45, 212, 191, 0.45) 0%, rgba(45, 212, 191, 0) 55%), linear-gradient(135deg, #134e4a 0%, #0f766e 55%, #0d9488 100%)",
      }}
    >
      <Box sx={{ position: "relative", zIndex: 1, maxWidth: { md: "55%" } }}>
        <Typography variant="overline" sx={{ opacity: 0.85 }}>
          Poland · Germany
        </Typography>
        <Typography variant="h3" component="h1" sx={{ fontSize: { xs: "2rem", md: "3rem" } }}>
          Find a car
        </Typography>
        <Typography sx={{ mt: 1.5, opacity: 0.9, fontSize: { md: "1.125rem" } }}>
          At airports and city centres{cities > 0 ? ` in ${cities} cities` : ""}. Pay when you pick
          up the car.
        </Typography>
      </Box>
      <Box
        aria-hidden
        sx={{
          display: { xs: "none", md: "block" },
          position: "absolute",
          right: 24,
          bottom: 40,
          width: "42%",
          filter: "drop-shadow(0 24px 24px rgba(0, 0, 0, 0.25))",
        }}
      >
        <CarPicture carType="SUV" carClass="PREMIUM" alt="" sx={{ background: "none" }} />
      </Box>
    </Box>
  );
}

const CLASS_CHIP: Record<string, { bg: string; fg: string }> = {
  ECONOMY: { bg: "#ffedd5", fg: "#9a3412" },
  COMFORT: { bg: "#dbeafe", fg: "#1e40af" },
  PREMIUM: { bg: "#1e293b", fg: "#f8fafc" },
};

function OfferCard({ offer, onChoose }: { offer: Offer; onChoose: () => void }) {
  const chip = CLASS_CHIP[offer.carClass] ?? CLASS_CHIP.ECONOMY!;
  return (
    <Card
      sx={{
        height: "100%",
        display: "flex",
        flexDirection: "column",
        overflow: "hidden",
        transition: "transform 160ms, box-shadow 160ms",
        "&:hover": {
          transform: "translateY(-3px)",
          boxShadow: "0 2px 4px rgba(15, 23, 42, 0.05), 0 20px 40px rgba(15, 23, 42, 0.12)",
        },
      }}
    >
      <CarPicture
        carType={offer.carType}
        carClass={offer.carClass}
        alt={`${offer.carTypeName}, ${offer.carClassName}`}
        sx={{ px: 3, pt: 3, pb: 1 }}
      />
      <CardContent sx={{ flexGrow: 1, pb: 1 }}>
        <Stack
          direction="row"
          spacing={1}
          sx={{ alignItems: "center", justifyContent: "space-between" }}
        >
          <Typography variant="h6" component="h2">
            {offer.carTypeName}
          </Typography>
          <Chip label={offer.carClassName} size="small" sx={{ bgcolor: chip.bg, color: chip.fg }} />
        </Stack>
        <Stack
          direction="row"
          spacing={0.75}
          sx={{ mt: 1, alignItems: "center", color: "text.secondary" }}
        >
          <EventAvailableIcon fontSize="small" />
          <Typography variant="body2">
            {offer.availableCars} {offer.availableCars === 1 ? "car" : "cars"} available
          </Typography>
        </Stack>
        <Typography variant="h5" component="p" sx={{ mt: 2 }}>
          {formatMoney(offer.dailyRate)}{" "}
          <Typography component="span" color="text.secondary" sx={{ fontWeight: 400 }}>
            per day
          </Typography>
        </Typography>
      </CardContent>
      <CardActions sx={{ px: 3, pb: 3 }}>
        <Button
          variant="contained"
          fullWidth
          onClick={onChoose}
          aria-label={`Choose ${offer.carTypeName} ${offer.carClassName}`}
          sx={{ minHeight: 44 }}
        >
          Choose
        </Button>
      </CardActions>
    </Card>
  );
}

function SearchForm({
  initial,
  onSearch,
}: {
  initial: SearchParams | null;
  onSearch: (params: SearchParams) => void;
}) {
  const countries = useCountries();
  const locations = useLocations();
  const carTypes = useCarTypes();
  const carClasses = useCarClasses();
  const all = locations.data ?? [];
  const [form, setForm] = useState<Form>(() => {
    const zone = all.find((l) => l.id === initial?.pickUp)?.timeZone;
    const clock = initial ? wallClock(initial.start, zone) : null;
    return {
      pickUp: initial?.pickUp ?? "",
      returnTo: initial?.returnTo ?? "",
      date: clock?.date ?? null,
      time: clock?.time ?? "10:00",
      days: String(initial?.days ?? 3),
      carType: initial?.carType ?? "",
      carClass: initial?.carClass ?? "",
    };
  });
  const [problem, setProblem] = useState<string | null>(null);
  const change = (patch: Partial<Form>) => setForm((current) => ({ ...current, ...patch }));

  const pickUp = all.find((l) => l.id === form.pickUp);
  const sameCountry = pickUp ? all.filter((l) => l.country === pickUp.country) : [];
  const countryName = (code: string) => countries.data?.find((c) => c.code === code)?.name ?? code;
  const days = Number(form.days);
  const stepDays = (by: number) =>
    change({
      days: String(Math.min(MAX_DAYS, Math.max(1, (Number.isInteger(days) ? days : 1) + by))),
    });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!pickUp) return setProblem("Choose where you pick up the car.");
    if (!form.date) return setProblem("Choose when you pick up the car.");
    if (!Number.isInteger(days) || days < 1 || days > MAX_DAYS)
      return setProblem(`A rental lasts from 1 to ${MAX_DAYS} days.`);
    const start = atWallClock(form.date, form.time, pickUp.timeZone);
    if (!start.isValid()) return setProblem("Choose when you pick up the car.");
    setProblem(null);
    onSearch({
      country: pickUp.country,
      pickUp: pickUp.id,
      returnTo: form.returnTo || pickUp.id,
      start: start.toISOString(),
      days,
      carType: form.carType || undefined,
      carClass: form.carClass || undefined,
    });
  };

  return (
    <Card component="form" onSubmit={submit} noValidate aria-label="Search">
      <CardContent sx={{ p: { xs: 2, sm: 3 } }}>
        <ErrorAlert
          error={locations.error ?? countries.error ?? (problem ? new Error(problem) : null)}
        />
        <Grid container spacing={2}>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              id="pick-up"
              select
              fullWidth
              required
              label="Pick-up location"
              value={form.pickUp}
              onChange={(e) => change({ pickUp: e.target.value, returnTo: "" })}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <PlaceIcon color="primary" />
                    </InputAdornment>
                  ),
                },
              }}
            >
              {groupByCountry(all).flatMap(([country, items]) => [
                <ListSubheader key={`country-${country}`} sx={{ fontWeight: 700 }}>
                  {countryName(country)}
                </ListSubheader>,
                ...items.map((l) => (
                  <MenuItem key={l.id} value={l.id}>
                    {l.name}
                  </MenuItem>
                )),
              ])}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField
              id="return-to"
              select
              fullWidth
              label="Return location"
              value={form.returnTo}
              disabled={!pickUp}
              onChange={(e) => change({ returnTo: e.target.value })}
              helperText="In the same country as the pick-up"
              slotProps={{
                select: { displayEmpty: true },
                inputLabel: { shrink: true },
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <KeyboardReturnIcon color={pickUp ? "primary" : "disabled"} />
                    </InputAdornment>
                  ),
                },
              }}
            >
              <MenuItem value="">Same as pick-up</MenuItem>
              {sameCountry
                .filter((l) => l.id !== form.pickUp)
                .map((l) => (
                  <MenuItem key={l.id} value={l.id}>
                    {l.name}
                  </MenuItem>
                ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <PickUpPicker
              value={{ date: form.date, time: form.time }}
              days={days}
              zone={pickUp?.timeZone}
              city={pickUp?.city}
              onChange={(value) => change(value)}
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              fullWidth
              required
              label="Days"
              type="number"
              value={form.days}
              onChange={(e) => change({ days: e.target.value })}
              helperText={`Up to ${MAX_DAYS} days`}
              slotProps={{
                htmlInput: {
                  min: 1,
                  max: MAX_DAYS,
                  inputMode: "numeric",
                  style: { textAlign: "center" },
                },
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <IconButton
                        aria-label="One day less"
                        onClick={() => stepDays(-1)}
                        disabled={Number.isInteger(days) && days <= 1}
                        edge="start"
                        sx={{ width: 40, height: 40 }}
                      >
                        <RemoveIcon />
                      </IconButton>
                    </InputAdornment>
                  ),
                  endAdornment: (
                    <InputAdornment position="end">
                      <IconButton
                        aria-label="One day more"
                        onClick={() => stepDays(1)}
                        disabled={Number.isInteger(days) && days >= MAX_DAYS}
                        edge="end"
                        sx={{ width: 40, height: 40 }}
                      >
                        <AddIcon />
                      </IconButton>
                    </InputAdornment>
                  ),
                },
              }}
              sx={{
                "& input::-webkit-outer-spin-button, & input::-webkit-inner-spin-button": {
                  WebkitAppearance: "none",
                  margin: 0,
                },
                "& input[type=number]": { MozAppearance: "textfield" },
              }}
            />
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <TextField
              id="car-type"
              select
              fullWidth
              label="Car type"
              value={form.carType}
              onChange={(e) => change({ carType: e.target.value })}
              slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
            >
              <MenuItem value="">Any type</MenuItem>
              {(carTypes.data ?? []).map((t) => (
                <MenuItem key={t.code} value={t.code}>
                  {t.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <TextField
              id="car-class"
              select
              fullWidth
              label="Car class"
              value={form.carClass}
              onChange={(e) => change({ carClass: e.target.value })}
              slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
            >
              <MenuItem value="">Any class</MenuItem>
              {(carClasses.data ?? []).map((c) => (
                <MenuItem key={c.code} value={c.code}>
                  {c.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, md: 4 }}>
            <Button
              type="submit"
              variant="contained"
              size="large"
              fullWidth
              startIcon={<SearchIcon />}
              sx={{ minHeight: 56 }}
            >
              Search
            </Button>
          </Grid>
        </Grid>
      </CardContent>
    </Card>
  );
}

function groupByCountry(locations: LocationInfo[]): [string, LocationInfo[]][] {
  const groups = new Map<string, LocationInfo[]>();
  for (const location of locations) {
    groups.set(location.country, [...(groups.get(location.country) ?? []), location]);
  }
  return [...groups.entries()];
}
