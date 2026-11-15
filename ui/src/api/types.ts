import type { components as Ancillary } from "./generated/ancillary";
import type { components as CarType } from "./generated/car-type";
import type { components as Location } from "./generated/location";
import type { components as Pricing } from "./generated/pricing";
import type { components as Reservations } from "./generated/reservation";
import type { components as Search } from "./generated/search";

export type Country = Location["schemas"]["Country"];
export type LocationInfo = Location["schemas"]["Location"];
export type CarKind = CarType["schemas"]["Named"];
export type SearchResult = Search["schemas"]["SearchResult"];
export type Offer = Search["schemas"]["Offer"];
export type Extra = Ancillary["schemas"]["Extra"];
export type Quote = Pricing["schemas"]["Quote"];
export type QuoteLine = Pricing["schemas"]["QuoteLine"];
export type Reservation = Reservations["schemas"]["Reservation"];
export type ReservationPrice = Reservations["schemas"]["Price"];
