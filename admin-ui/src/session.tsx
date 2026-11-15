import { Alert, Box, Button, CircularProgress, Paper, Stack, Typography } from "@mui/material";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useContext, useEffect, type ReactNode } from "react";
import { SIGNED_OUT_EVENT, gateway, unwrap } from "./api/clients";
import type { components } from "./api/generated/gateway";
import { CountryProvider } from "./country";
import { ErrorAlert } from "./components/ErrorAlert";

type Session = components["schemas"]["Session"];

export type SignedIn = {
  name: string;
  /** Administers all countries and the shared reference data. */
  superAdmin: boolean;
  /** The only country of a country administrator, or null for a super administrator. */
  country: string | null;
};

/** Leaves the page. Tests replace it. */
export const browser = {
  go: (url: string) => globalThis.location.assign(url),
};

export const SIGN_IN_URL = "/oauth2/authorization/keycloak";

export async function signOut() {
  const { redirectTo } = await unwrap(gateway.POST("/logout"));
  browser.go(redirectTo);
}

const SessionContext = createContext<SignedIn | null>(null);

/** The signed-in administrator. Only for pages inside SessionGate. */
export function useSession(): SignedIn {
  const session = useContext(SessionContext);
  if (!session) {
    throw new Error("useSession must be used inside SessionGate");
  }
  return session;
}

function administrator(session: Session): SignedIn | null {
  const superAdmin = session.roles.includes("SUPER_ADMIN");
  if (!superAdmin && !session.roles.includes("COUNTRY_ADMIN")) {
    return null;
  }
  return {
    name: session.name ?? "",
    superAdmin,
    country: superAdmin ? null : (session.country ?? null),
  };
}

/**
 * Shows its content only to a signed-in administrator. Anyone else sees a sign-in page, or a
 * refusal for an account that is not an administrator.
 */
export function SessionGate({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const session = useQuery({
    queryKey: ["session"],
    queryFn: () => unwrap(gateway.GET("/api/v1/session")),
    staleTime: Infinity,
  });

  useEffect(() => {
    const refresh = () => void queryClient.invalidateQueries({ queryKey: ["session"] });
    globalThis.addEventListener(SIGNED_OUT_EVENT, refresh);
    return () => globalThis.removeEventListener(SIGNED_OUT_EVENT, refresh);
  }, [queryClient]);

  if (session.isPending) {
    return (
      <Centered>
        <CircularProgress aria-label="Loading" />
      </Centered>
    );
  }
  if (session.isError) {
    return (
      <Centered>
        <ErrorAlert error={session.error} />
      </Centered>
    );
  }
  if (!session.data.signedIn) {
    return <SignInPage />;
  }
  const admin = administrator(session.data);
  if (!admin) {
    return (
      <Centered>
        <Typography variant="h5" component="h1">
          No access
        </Typography>
        <Alert severity="warning">
          The account {session.data.name} is not an administrator account.
        </Alert>
        <Button variant="outlined" onClick={() => void signOut()} sx={{ minHeight: 44 }}>
          Sign out
        </Button>
      </Centered>
    );
  }
  return (
    <SessionContext value={admin}>
      <CountryProvider fixed={admin.country}>{children}</CountryProvider>
    </SessionContext>
  );
}

function SignInPage() {
  const failed = new URLSearchParams(globalThis.location?.search).has("signInFailed");
  return (
    <Centered>
      <Typography variant="h5" component="h1">
        Car Rental Admin
      </Typography>
      <Typography color="text.secondary">Sign in with your administrator account.</Typography>
      {failed && <Alert severity="error">The sign-in failed. Please try again.</Alert>}
      <Button variant="contained" onClick={() => browser.go(SIGN_IN_URL)} sx={{ minHeight: 44 }}>
        Sign in
      </Button>
    </Centered>
  );
}

function Centered({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ minHeight: "100vh", display: "grid", placeItems: "center", p: 2 }}>
      <Paper sx={{ p: { xs: 3, sm: 4 }, width: "100%", maxWidth: 420 }}>
        <Stack spacing={2} sx={{ alignItems: "stretch", textAlign: "center" }}>
          {children}
        </Stack>
      </Paper>
    </Box>
  );
}
