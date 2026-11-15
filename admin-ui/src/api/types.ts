import type { components as Administration } from "./generated/administration";
import type { components as Ancillary } from "./generated/ancillary";
import type { components as Discount } from "./generated/discount";
import type { components as Inventory } from "./generated/inventory";
import type { components as Pricing } from "./generated/pricing";
import type { components as Reservations } from "./generated/reservation";

export type Country = Administration["schemas"]["Country"];
export type Named = Administration["schemas"]["Named"];
export type Location = Administration["schemas"]["Location"];
export type Stock = Inventory["schemas"]["Stock"];
export type Transfer = Inventory["schemas"]["Transfer"];
export type Rate = Pricing["schemas"]["Rate"];
export type Money = Pricing["schemas"]["Money"];
export type Extra = Ancillary["schemas"]["Extra"];
export type DiscountRule = Discount["schemas"]["DiscountRule"];
export type DiscountRuleRequest = Discount["schemas"]["DiscountRuleRequest"];
export type Reservation = Reservations["schemas"]["Reservation"];
