import dayjs from "dayjs";
import timezone from "dayjs/plugin/timezone";
import utc from "dayjs/plugin/utc";

dayjs.extend(utc);
dayjs.extend(timezone);

/** Shows a moment in the local time of a location, as staff at that location see it. */
export function formatInZone(instant: string, zone: string | undefined): string {
  const moment = zone ? dayjs(instant).tz(zone) : dayjs(instant);
  return moment.format("YYYY-MM-DD HH:mm");
}

export { dayjs };
