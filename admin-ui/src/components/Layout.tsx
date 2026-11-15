import LogoutIcon from "@mui/icons-material/Logout";
import MenuIcon from "@mui/icons-material/Menu";
import {
  AppBar,
  Box,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Select,
  Toolbar,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { NavLink, Outlet } from "react-router";
import { administration, unwrap } from "../api/clients";
import { useCountry } from "../country";
import { signOut, useSession } from "../session";

const DRAWER_WIDTH = 220;

const pages = [
  { path: "/countries", label: "Countries" },
  { path: "/car-types", label: "Car types" },
  { path: "/car-classes", label: "Car classes" },
  { path: "/locations", label: "Locations" },
  { path: "/stock", label: "Stock" },
  { path: "/transfers", label: "Transfers" },
  { path: "/reservations", label: "Reservations" },
  { path: "/rates", label: "Rates" },
  { path: "/extras", label: "Extras" },
  { path: "/discounts", label: "Discounts" },
];

/** The frame of every page: a top bar with the country and the user, and the navigation. */
export function Layout() {
  const theme = useTheme();
  const wide = useMediaQuery(theme.breakpoints.up("md"));
  const [open, setOpen] = useState(false);
  const { country, setCountry, choosable } = useCountry();
  const { name } = useSession();
  const countries = useQuery({
    queryKey: ["countries"],
    queryFn: () => unwrap(administration.GET("/api/v1/admin/countries")),
  });

  const navigation = (
    <List component="nav" aria-label="Sections">
      {pages.map((page) => (
        <ListItemButton
          key={page.path}
          component={NavLink}
          to={page.path}
          onClick={() => setOpen(false)}
          sx={{ "&.active": { bgcolor: "action.selected" } }}
        >
          <ListItemText primary={page.label} />
        </ListItemButton>
      ))}
    </List>
  );

  return (
    <Box sx={{ display: "flex", minHeight: "100vh" }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar sx={{ gap: 1 }}>
          {!wide && (
            <IconButton
              color="inherit"
              edge="start"
              aria-label="Open navigation"
              onClick={() => setOpen(true)}
              sx={{ minWidth: 44, minHeight: 44 }}
            >
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="h6" component="h1" sx={{ flexGrow: 1 }} noWrap>
            Car Rental Admin
          </Typography>
          {choosable ? (
            <Select
              size="small"
              value={country ?? ""}
              onChange={(event) => setCountry(event.target.value)}
              displayEmpty
              inputProps={{ "aria-label": "Country" }}
              sx={{ minWidth: 140, bgcolor: "background.paper" }}
            >
              <MenuItem value="" disabled>
                Choose a country
              </MenuItem>
              {(countries.data ?? []).map((c) => (
                <MenuItem key={c.code} value={c.code}>
                  {c.name}
                </MenuItem>
              ))}
            </Select>
          ) : (
            <Typography aria-label="Country" noWrap>
              {countries.data?.find((c) => c.code === country)?.name ?? country}
            </Typography>
          )}
          {wide && (
            <Typography variant="body2" noWrap sx={{ maxWidth: 200, opacity: 0.9 }}>
              {name}
            </Typography>
          )}
          <IconButton
            color="inherit"
            edge="end"
            aria-label="Sign out"
            title="Sign out"
            onClick={() => void signOut()}
            sx={{ minWidth: 44, minHeight: 44 }}
          >
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>
      <Drawer
        variant={wide ? "permanent" : "temporary"}
        open={wide || open}
        onClose={() => setOpen(false)}
        sx={{ width: DRAWER_WIDTH, "& .MuiDrawer-paper": { width: DRAWER_WIDTH } }}
      >
        <Toolbar />
        {navigation}
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, p: { xs: 2, md: 3 }, minWidth: 0 }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
