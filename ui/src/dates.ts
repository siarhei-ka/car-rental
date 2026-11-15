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

/** The time now in a location, or in the browser when the location is not known yet. */
export function nowIn(zone: string | undefined) {
  return zone ? dayjs().tz(zone) : dayjs();
}

/** The day and the time of a moment on the clocks of a location. */
export function wallClock(instant: string, zone: string | undefined) {
  const moment = zone ? dayjs(instant).tz(zone) : dayjs(instant);
  return { date: moment.format("YYYY-MM-DD"), time: moment.format("HH:mm") };
}

/** The moment when the clocks of a location show the given day and time. */
export function atWallClock(date: string, time: string, zone: string | undefined) {
  const local = `${date}T${time}`;
  return zone ? dayjs.tz(local, zone) : dayjs(local);
}

export { dayjs };
