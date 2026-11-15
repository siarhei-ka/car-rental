import "@fontsource-variable/inter";
import { alpha, createTheme } from "@mui/material";

const ink = "#0f172a";
const line = "#e2e8f0";
const brand = "#0f766e";

/** The look of the customer site: one brand colour, soft surfaces, rounded corners, Inter. */
export const theme = createTheme({
  palette: {
    primary: { main: brand, light: "#14b8a6", dark: "#115e59", contrastText: "#ffffff" },
    secondary: { main: "#f97316", contrastText: "#ffffff" },
    background: { default: "#f5f7fb", paper: "#ffffff" },
    text: { primary: ink, secondary: "#64748b" },
    divider: line,
  },
  shape: { borderRadius: 14 },
  typography: {
    fontFamily:
      '"Inter Variable", "Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h1: { fontWeight: 800, letterSpacing: "-0.03em" },
    h2: { fontWeight: 800, letterSpacing: "-0.025em" },
    h3: { fontWeight: 700, letterSpacing: "-0.02em" },
    h4: { fontWeight: 800, letterSpacing: "-0.02em" },
    h5: { fontWeight: 700, letterSpacing: "-0.015em" },
    h6: { fontWeight: 700, letterSpacing: "-0.01em" },
    subtitle1: { fontWeight: 600 },
    button: { textTransform: "none", fontWeight: 600, letterSpacing: 0 },
    overline: { fontWeight: 700, letterSpacing: "0.08em" },
  },
  components: {
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: { borderRadius: 12 },
        sizeLarge: { paddingInline: 24, fontSize: "1rem" },
      },
      variants: [
        {
          props: { variant: "contained", color: "primary" },
          style: {
            backgroundImage: `linear-gradient(135deg, #14b8a6 0%, ${brand} 55%, #115e59 100%)`,
            boxShadow: `0 6px 16px ${alpha(brand, 0.28)}`,
            "&:hover": { boxShadow: `0 8px 22px ${alpha(brand, 0.36)}` },
            "&.Mui-disabled": { backgroundImage: "none", boxShadow: "none" },
          },
        },
      ],
    },
    MuiCard: {
      defaultProps: { elevation: 0 },
      styleOverrides: {
        root: {
          border: `1px solid ${line}`,
          boxShadow: "0 1px 2px rgba(15, 23, 42, 0.04), 0 12px 32px rgba(15, 23, 42, 0.06)",
        },
      },
    },
    MuiCardContent: { styleOverrides: { root: { padding: 24 } } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          backgroundColor: "#ffffff",
          "& .MuiOutlinedInput-notchedOutline": { borderColor: line },
          "&:hover .MuiOutlinedInput-notchedOutline": { borderColor: "#94a3b8" },
        },
      },
    },
    MuiChip: { styleOverrides: { root: { fontWeight: 600 } } },
    MuiAlert: { styleOverrides: { root: { borderRadius: 12 } } },
    MuiMenu: {
      styleOverrides: {
        paper: { borderRadius: 12, boxShadow: "0 12px 32px rgba(15, 23, 42, 0.14)" },
      },
    },
  },
});
