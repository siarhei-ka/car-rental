/**
 * A search as it is kept in the address bar, so that a result can be shared or reloaded. After a
 * car is chosen, the insurance option and the extras are kept there too, so that the choice
 * survives the sign-in.
 */
export type SearchParams = {
  country: string;
  pickUp: string;
  returnTo: string;
  start: string;
  days: number;
  carType?: string;
  carClass?: string;
  /** The code of the chosen insurance option, or none for the preselected one. */
  insurance?: string;
  /** How many of each extra, by code. Extras not chosen are left out. */
  extras?: Record<string, number>;
};

export function toQuery(params: SearchParams): URLSearchParams {
  const query = new URLSearchParams({
    country: params.country,
    pickUp: params.pickUp,
    returnTo: params.returnTo,
    start: params.start,
    days: String(params.days),
  });
  if (params.carType) query.set("carType", params.carType);
  if (params.carClass) query.set("carClass", params.carClass);
  if (params.insurance) query.set("insurance", params.insurance);
  const extras = Object.entries(params.extras ?? {}).filter(([, quantity]) => quantity > 0);
  if (extras.length > 0) {
    query.set("extras", extras.map(([code, quantity]) => `${code}:${quantity}`).join(","));
  }
  return query;
}

/** Reads a search from the address bar, or null if it is incomplete. */
export function fromQuery(query: URLSearchParams): SearchParams | null {
  const country = query.get("country");
  const pickUp = query.get("pickUp");
  const start = query.get("start");
  const days = Number(query.get("days"));
  if (!country || !pickUp || !start || !Number.isInteger(days) || days < 1) {
    return null;
  }
  return {
    country,
    pickUp,
    returnTo: query.get("returnTo") ?? pickUp,
    start,
    days,
    carType: query.get("carType") ?? undefined,
    carClass: query.get("carClass") ?? undefined,
    insurance: query.get("insurance") ?? undefined,
    extras: parseExtras(query.get("extras")),
  };
}

/** "CHILD_SEAT:2,GPS:1" as {CHILD_SEAT: 2, GPS: 1}. Malformed parts are skipped. */
function parseExtras(text: string | null): Record<string, number> {
  const extras: Record<string, number> = {};
  for (const part of (text ?? "").split(",")) {
    const [code, count] = part.split(":");
    const quantity = Number(count);
    if (code && /^[A-Z][A-Z0-9_]*$/.test(code) && Number.isInteger(quantity) && quantity > 0) {
      extras[code] = quantity;
    }
  }
  return extras;
}
