import type { Reservation } from "./api/types";

/** The state of a reservation as the customer reads it. */
export const STATES: Record<Reservation["state"], string> = {
  CONFIRMED: "Confirmed",
  CANCELLED: "Cancelled",
  PICKED_UP: "Picked up",
  RETURNED: "Returned",
  NO_SHOW: "Not picked up",
};

/** A reservation can be cancelled while it is confirmed and has not started. */
export function canCancel(reservation: Reservation, now: Date = new Date()): boolean {
  return reservation.state === "CONFIRMED" && new Date(reservation.start) > now;
}
