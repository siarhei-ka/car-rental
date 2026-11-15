import CalendarMonthIcon from "@mui/icons-material/CalendarMonth";
import CloseIcon from "@mui/icons-material/Close";
import {
  alpha,
  Box,
  Button,
  ButtonBase,
  Dialog,
  Divider,
  FormHelperText,
  IconButton,
  Popover,
  Stack,
  Typography,
  useMediaQuery,
  useTheme,
} from "@mui/material";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import { DateCalendar } from "@mui/x-date-pickers/DateCalendar";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { PickerDay, type PickerDayProps } from "@mui/x-date-pickers/PickerDay";
import type { Dayjs } from "dayjs";
import "dayjs/locale/en-gb";
import { createContext, useContext, useId, useState } from "react";
import { dayjs, nowIn } from "../dates";

/** The pick-up times on offer, every half hour. */
const SLOTS = Array.from({ length: 48 }, (_, i) => {
  const hour = String(Math.floor(i / 2)).padStart(2, "0");
  return `${hour}:${i % 2 === 0 ? "00" : "30"}`;
});

const PARTS_OF_DAY = [
  { name: "Morning", from: "06:00", to: "12:00" },
  { name: "Afternoon", from: "12:00", to: "18:00" },
  { name: "Evening", from: "18:00", to: "24:00" },
  { name: "Night", from: "00:00", to: "06:00" },
];

export type PickUp = { date: string | null; time: string };

/** The rental days, from the pick-up day to the return day, as the calendar marks them. */
const Rental = createContext<{ first: Dayjs | null; last: Dayjs | null }>({
  first: null,
  last: null,
});

/**
 * Where the customer picks the pick-up day and time, in the local time of the pick-up location.
 * The calendar marks every day of the rental up to the return day. Times are offered every half
 * hour; past ones are not. On a phone the picker fills the screen, elsewhere it opens below the
 * field.
 */
export function PickUpPicker({
  value,
  days,
  zone,
  city,
  onChange,
}: {
  value: PickUp;
  days: number;
  zone: string | undefined;
  city: string | undefined;
  onChange: (value: PickUp) => void;
}) {
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down("sm"));
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const open = anchor != null;
  const close = () => setAnchor(null);
  const labelId = useId();
  const helperId = useId();

  const shown = value.date
    ? `${dayjs(value.date).format("ddd, D MMM YYYY")} · ${value.time}`
    : null;
  const helper = city ? `Local time in ${city}` : "Local time of the location";
  const panel = <Panel value={value} days={days} zone={zone} onChange={onChange} onDone={close} />;

  return (
    <Box>
      <ButtonBase
        onClick={(event) => setAnchor(event.currentTarget)}
        aria-haspopup="dialog"
        aria-label={`Pick-up date and time: ${shown ?? "not chosen"}`}
        aria-describedby={helperId}
        sx={{
          width: "100%",
          minHeight: 56,
          px: 1.75,
          gap: 1.5,
          justifyContent: "flex-start",
          textAlign: "left",
          borderRadius: "12px",
          border: 1,
          borderColor: open ? "primary.main" : "divider",
          boxShadow: open ? `0 0 0 3px ${alpha(theme.palette.primary.main, 0.15)}` : "none",
          bgcolor: "background.paper",
          transition: "border-color 120ms, box-shadow 120ms",
          "&:hover": { borderColor: open ? "primary.main" : "#94a3b8" },
        }}
      >
        <CalendarMonthIcon color="primary" />
        <Box sx={{ minWidth: 0 }}>
          <Typography id={labelId} variant="caption" color="text.secondary" component="div">
            Pick-up date and time *
          </Typography>
          <Typography
            component="div"
            sx={{ fontWeight: shown ? 600 : 400, color: shown ? "text.primary" : "text.secondary" }}
          >
            {shown ?? "Choose date and time"}
          </Typography>
        </Box>
      </ButtonBase>
      <FormHelperText id={helperId} sx={{ mx: 1.75 }}>
        {helper}
      </FormHelperText>
      {phone ? (
        <Dialog open={open} onClose={close} fullScreen aria-labelledby={labelId}>
          {panel}
        </Dialog>
      ) : (
        <Popover
          open={open}
          anchorEl={anchor}
          onClose={close}
          anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
          transformOrigin={{ vertical: "top", horizontal: "left" }}
          slotProps={{
            paper: {
              role: "dialog",
              "aria-labelledby": labelId,
              sx: { mt: 1, borderRadius: "20px", boxShadow: "0 24px 48px rgba(15, 23, 42, 0.18)" },
            },
          }}
        >
          {panel}
        </Popover>
      )}
    </Box>
  );
}

function Panel({
  value,
  days,
  zone,
  onChange,
  onDone,
}: {
  value: PickUp;
  days: number;
  zone: string | undefined;
  onChange: (value: PickUp) => void;
  onDone: () => void;
}) {
  const now = nowIn(zone);
  const today = now.format("YYYY-MM-DD");
  const freeToday = SLOTS.filter((slot) => slot > now.format("HH:mm"));
  const firstDay = freeToday.length > 0 ? today : now.add(1, "day").format("YYYY-MM-DD");
  const isFree = (date: string | null, slot: string) => date !== today || freeToday.includes(slot);

  const chooseDay = (day: Dayjs | null) => {
    if (!day) return;
    const date = day.format("YYYY-MM-DD");
    const time = isFree(date, value.time) ? value.time : (freeToday[0] ?? value.time);
    onChange({ date, time });
  };

  const first = value.date ? dayjs(value.date) : null;
  const last = first && Number.isInteger(days) && days > 0 ? first.add(days, "day") : null;
  const returnDay = last ? `${last.format("ddd, D MMM")}, ${value.time}` : null;

  return (
    <Stack sx={{ height: "100%" }}>
      <Stack direction="row" spacing={1} sx={{ px: 3, pt: 2.5, pb: 1, alignItems: "flex-start" }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6" component="h2">
            When do you pick up the car?
          </Typography>
          <Typography variant="body2" color="text.secondary">
            The marked days are your rental, up to the return day.
          </Typography>
        </Box>
        <IconButton aria-label="Close" onClick={onDone} edge="end" sx={{ width: 44, height: 44 }}>
          <CloseIcon />
        </IconButton>
      </Stack>
      <Stack
        direction={{ xs: "column", sm: "row" }}
        divider={<Divider flexItem orientation="vertical" />}
        sx={{ flexGrow: 1, overflow: "auto" }}
      >
        <Rental.Provider value={{ first, last }}>
          <LocalizationProvider dateAdapter={AdapterDayjs} adapterLocale="en-gb">
            <DateCalendar
              value={first}
              onChange={chooseDay}
              minDate={dayjs(firstDay)}
              maxDate={dayjs(today).add(1, "year")}
              views={["day"]}
              showDaysOutsideCurrentMonth={false}
              slots={{ day: RentalDay }}
              sx={calendarLook}
            />
          </LocalizationProvider>
        </Rental.Provider>
        <Box
          role="group"
          aria-label="Pick-up time"
          sx={{
            px: 2,
            py: 1.5,
            flexShrink: 0,
            width: { sm: 248 },
            maxHeight: { sm: 352 },
            overflowY: { sm: "auto" },
          }}
        >
          {PARTS_OF_DAY.map((part) => (
            <Box key={part.name} sx={{ mb: 1.5 }}>
              <Typography variant="overline" color="text.secondary" component="div">
                {part.name}
              </Typography>
              <Box sx={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 0.75 }}>
                {SLOTS.filter((slot) => slot >= part.from && slot < part.to).map((slot) => {
                  const chosen = slot === value.time;
                  return (
                    <Button
                      key={slot}
                      size="small"
                      variant={chosen ? "contained" : "outlined"}
                      aria-pressed={chosen}
                      disabled={!isFree(value.date, slot)}
                      onClick={() => onChange({ date: value.date, time: slot })}
                      sx={{
                        minHeight: 40,
                        minWidth: 0,
                        fontVariantNumeric: "tabular-nums",
                        ...(!chosen && { borderColor: "divider", color: "text.primary" }),
                      }}
                    >
                      {slot}
                    </Button>
                  );
                })}
              </Box>
            </Box>
          ))}
        </Box>
      </Stack>
      <Divider />
      <Stack
        direction="row"
        spacing={2}
        sx={{ px: 3, py: 2, alignItems: "center", justifyContent: "space-between" }}
      >
        <Typography variant="body2" color="text.secondary">
          {returnDay ? (
            <>
              Return{" "}
              <Box component="strong" sx={{ color: "text.primary" }}>
                {returnDay}
              </Box>
            </>
          ) : (
            "Choose a day"
          )}
        </Typography>
        <Button variant="contained" onClick={onDone} disabled={!value.date} sx={{ minHeight: 44 }}>
          Done
        </Button>
      </Stack>
    </Stack>
  );
}

/** A day of the calendar; the days of the rental are joined into one band. */
function RentalDay(props: PickerDayProps) {
  const { first, last } = useContext(Rental);
  const day = props.day as Dayjs;
  const inRental =
    first != null && last != null && !day.isBefore(first, "day") && !day.isAfter(last, "day");
  // The band is rounded where the rental starts or ends, and where a week row starts or ends.
  const isFirst = (first != null && day.isSame(first, "day")) || day.day() === 1;
  const isLast = (last != null && day.isSame(last, "day")) || day.day() === 0;
  const isReturn = last != null && day.isSame(last, "day");
  return (
    <PickerDay
      {...props}
      sx={(theme) => ({
        ...(inRental &&
          !props.outsideCurrentMonth && {
            borderRadius: 0,
            bgcolor: alpha(theme.palette.primary.main, 0.12),
            ...(isFirst && { borderTopLeftRadius: 20, borderBottomLeftRadius: 20 }),
            ...(isLast && { borderTopRightRadius: 20, borderBottomRightRadius: 20 }),
            ...(isReturn && { bgcolor: alpha(theme.palette.primary.main, 0.28), fontWeight: 700 }),
          }),
      })}
    />
  );
}

const calendarLook = {
  m: 0,
  flexShrink: 0,
  alignSelf: { xs: "center", sm: "flex-start" },
  width: 320,
  maxWidth: "100%",
  height: "auto",
  maxHeight: "none",
  "& .MuiPickersCalendarHeader-root": { pl: 2.5, pr: 1.5, mt: 1.5 },
  "& .MuiPickersCalendarHeader-label": { fontWeight: 700, fontSize: "1rem" },
  "& .MuiDayCalendar-weekDayLabel": {
    width: 40,
    height: 32,
    m: 0,
    fontWeight: 600,
    color: "text.secondary",
  },
  "& .MuiDayCalendar-slideTransition": { minHeight: 264 },
  "& .MuiPickerDay-root": { width: 40, height: 40, m: 0, fontSize: "0.9rem" },
  "& .MuiPickerDay-root.Mui-selected": { borderRadius: 20, fontWeight: 700 },
  "& .MuiPickerDay-root.MuiPickerDay-today:not(.Mui-selected)": { borderColor: "primary.light" },
} as const;
