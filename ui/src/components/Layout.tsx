import DirectionsCarIcon from "@mui/icons-material/DirectionsCar";
import LogoutIcon from "@mui/icons-material/Logout";
import EventNoteIcon from "@mui/icons-material/EventNote";
import PersonIcon from "@mui/icons-material/Person";
import {
  Alert,
  alpha,
  AppBar,
  Box,
  Button,
  Container,
  IconButton,
  Link,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { Outlet, Link as RouterLink, useLocation } from "react-router";
import { AfterSignIn, signIn, signOut, useSession } from "../session";

/** The frame of every page, with sign-in and sign-out. */
export function Layout() {
  const location = useLocation();
  const signInFailed = new URLSearchParams(location.search).has("signInFailed");

  return (
    <Box sx={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
      <AfterSignIn />
      <AppBar
        position="sticky"
        elevation={0}
        color="inherit"
        sx={{
          bgcolor: (theme) => alpha(theme.palette.background.paper, 0.82),
          backdropFilter: "saturate(180%) blur(12px)",
          borderBottom: 1,
          borderColor: "divider",
        }}
      >
        <Container maxWidth="lg" disableGutters>
          <Toolbar sx={{ gap: 1 }}>
            <Link
              component={RouterLink}
              to="/"
              color="inherit"
              underline="none"
              sx={{ minHeight: 44, display: "flex", alignItems: "center", gap: 1.25, flexGrow: 1 }}
            >
              <Box
                aria-hidden
                sx={{
                  width: 36,
                  height: 36,
                  borderRadius: "10px",
                  display: "grid",
                  placeItems: "center",
                  color: "common.white",
                  backgroundImage: "linear-gradient(135deg, #14b8a6 0%, #0f766e 60%, #115e59 100%)",
                  boxShadow: "0 4px 12px rgba(15, 118, 110, 0.35)",
                }}
              >
                <DirectionsCarIcon fontSize="small" />
              </Box>
              <Typography variant="h6" component="span" sx={{ fontWeight: 800 }}>
                Car Rental
              </Typography>
            </Link>
            <Account />
          </Toolbar>
        </Container>
      </AppBar>
      <Container component="main" maxWidth="lg" sx={{ py: { xs: 2, md: 4 }, flexGrow: 1 }}>
        {signInFailed && (
          <Alert severity="error" sx={{ mb: 2 }}>
            The sign-in failed. Please try again.
          </Alert>
        )}
        <Outlet />
      </Container>
      <Box component="footer" sx={{ borderTop: 1, borderColor: "divider", py: 3 }}>
        <Container maxWidth="lg">
          <Typography variant="body2" color="text.secondary">
            Car Rental · Poland and Germany · Pay at pick-up
          </Typography>
        </Container>
      </Box>
    </Box>
  );
}

function Account() {
  const session = useSession();
  const location = useLocation();
  const wide = useMediaQuery(useTheme().breakpoints.up("sm"));

  if (session.state === "loading") {
    return null;
  }
  if (session.state === "signed-out") {
    return (
      <Button
        color="inherit"
        onClick={() => signIn(location.pathname + location.search)}
        sx={{ minHeight: 44 }}
      >
        Sign in
      </Button>
    );
  }
  return (
    <>
      {session.state === "customer" && (
        <Button
          component={RouterLink}
          to="/reservations"
          color="inherit"
          startIcon={<EventNoteIcon />}
          aria-label="Your reservations"
          sx={{ minHeight: 44 }}
        >
          {wide ? "Reservations" : null}
        </Button>
      )}
      {session.state === "customer" && (
        <Button
          component={RouterLink}
          to="/profile"
          color="inherit"
          startIcon={<PersonIcon />}
          aria-label="Driver profile"
          sx={{ minHeight: 44 }}
        >
          {wide ? session.name : "Profile"}
        </Button>
      )}
      <IconButton
        color="inherit"
        aria-label="Sign out"
        onClick={() => void signOut()}
        sx={{ minWidth: 44, minHeight: 44 }}
      >
        <LogoutIcon />
      </IconButton>
    </>
  );
}
