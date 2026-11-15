import PlaceOutlinedIcon from "@mui/icons-material/PlaceOutlined";
import ScheduleIcon from "@mui/icons-material/Schedule";
import { Stack, Typography } from "@mui/material";
import type { SearchResult } from "../api/types";
import { formatInZone } from "../dates";

/** Where and when, in the local time of each location. */
export function TripSummary({ result }: { result: SearchResult }) {
  const from = result.pickUpLocation;
  const to = result.returnLocation;
  return (
    <Stack spacing={0.5}>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <PlaceOutlinedIcon fontSize="small" color="primary" />
        <Typography sx={{ fontWeight: 600 }}>
          {from.name}
          {from.id !== to.id && ` → ${to.name}`}
        </Typography>
      </Stack>
      <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
        <ScheduleIcon fontSize="small" color="primary" />
        <Typography color="text.secondary">
          {formatInZone(result.start, from.timeZone)} – {formatInZone(result.end, to.timeZone)} ·{" "}
          {result.days} {result.days === 1 ? "day" : "days"} · local times
        </Typography>
      </Stack>
    </Stack>
  );
}
