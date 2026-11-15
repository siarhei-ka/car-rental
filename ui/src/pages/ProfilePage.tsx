import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { Dayjs } from "dayjs";
import { useState, type FormEvent } from "react";
import { useNavigate, useSearchParams } from "react-router";
import { customerApi, unwrap } from "../api/clients";
import { ErrorAlert } from "../components/ErrorAlert";
import { COUNTRIES, type Country } from "../countries";
import { dayjs } from "../dates";
import { signIn, signOut, useProfile, useSession, type Profile } from "../session";

/** A driver is an adult. Whether the driver is old enough to rent is checked for each rental. */
const MINIMUM_AGE = 18;

/**
 * The driver profile of the signed-in customer: the date of birth and the country that issued the
 * driving licence. After saving, the page returns to where the customer came from.
 */
export function ProfilePage() {
  const session = useSession();
  const profile = useProfile(session.state === "customer");
  const [query] = useSearchParams();
  const next = safeNext(query.get("next"));

  if (session.state === "loading" || (session.state === "customer" && profile.isPending)) {
    return <CircularProgress aria-label="Loading" />;
  }
  if (session.state === "signed-out") {
    return (
      <Stack spacing={2} sx={{ alignItems: "flex-start" }}>
        <Typography variant="h4" component="h1">
          Driver profile
        </Typography>
        <Typography>Sign in to enter your driver profile.</Typography>
        <Button variant="contained" onClick={() => signIn("/profile")} sx={{ minHeight: 44 }}>
          Sign in
        </Button>
      </Stack>
    );
  }
  if (session.state === "not-customer") {
    return (
      <Stack spacing={2} sx={{ alignItems: "flex-start" }}>
        <Typography variant="h4" component="h1">
          Driver profile
        </Typography>
        <Alert severity="warning">
          The account {session.name} is not a customer account. Sign out and sign in as a customer.
        </Alert>
        <Button variant="outlined" onClick={() => void signOut()} sx={{ minHeight: 44 }}>
          Sign out
        </Button>
      </Stack>
    );
  }
  if (profile.isError) {
    return <ErrorAlert error={profile.error} />;
  }
  return <ProfileForm saved={profile.data ?? null} next={next} />;
}

function ProfileForm({ saved, next }: { saved: Profile | null; next: string | null }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [dateOfBirth, setDateOfBirth] = useState<Dayjs | null>(
    saved ? dayjs(saved.dateOfBirth) : null,
  );
  const [country, setCountry] = useState<Country | null>(
    COUNTRIES.find((c) => c.code === saved?.licenceCountry) ?? null,
  );
  const [done, setDone] = useState(false);
  const latest = dayjs().subtract(MINIMUM_AGE, "year");

  const save = useMutation({
    mutationFn: (body: Profile) => unwrap(customerApi.PUT("/api/v1/profile", { body })),
    onSuccess: (profile) => {
      queryClient.setQueryData(["profile"], profile);
      if (next) {
        void navigate(next);
      } else {
        setDone(true);
      }
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!dateOfBirth?.isValid() || !country) {
      return;
    }
    setDone(false);
    save.mutate({ dateOfBirth: dateOfBirth.format("YYYY-MM-DD"), licenceCountry: country.code });
  }

  return (
    <Stack spacing={3} sx={{ maxWidth: 640, mx: "auto", width: "100%" }}>
      <Typography variant="h4" component="h1">
        Driver profile
      </Typography>
      {!saved && (
        <Alert severity="info">
          Before you see prices and reserve, please enter your date of birth and the country that
          issued your driving licence. Insurance prices and the minimum age of the driver depend on
          them.
        </Alert>
      )}
      <Card variant="outlined">
        <CardContent>
          <Box component="form" onSubmit={submit} noValidate>
            <Stack spacing={2}>
              <DatePicker
                label="Date of birth"
                value={dateOfBirth}
                onChange={setDateOfBirth}
                maxDate={latest}
                openTo="year"
                views={["year", "month", "day"]}
                format="YYYY-MM-DD"
                slotProps={{
                  openPickerButton: { sx: { minWidth: 44, minHeight: 44 } },
                  textField: {
                    fullWidth: true,
                    required: true,
                    helperText: `You must be at least ${MINIMUM_AGE} years old`,
                  },
                }}
              />
              <Autocomplete
                options={COUNTRIES}
                value={country}
                onChange={(_, value) => setCountry(value)}
                getOptionLabel={(option) => option.name}
                isOptionEqualToValue={(option, value) => option.code === value.code}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label="Country that issued your driving licence"
                    required
                  />
                )}
              />
              <ErrorAlert error={save.error} />
              {done && <Alert severity="success">Your driver profile is saved.</Alert>}
              <Button
                type="submit"
                variant="contained"
                disabled={!dateOfBirth?.isValid() || !country || save.isPending}
                sx={{ minHeight: 48, alignSelf: "flex-start" }}
              >
                Save
              </Button>
            </Stack>
          </Box>
        </CardContent>
      </Card>
    </Stack>
  );
}

/** Only an address inside the UI, so that the parameter cannot send the customer elsewhere. */
function safeNext(next: string | null): string | null {
  return next && next.startsWith("/") && !next.startsWith("//") ? next : null;
}
