import createClient, { type Middleware } from "openapi-fetch";
import type { paths as AdministrationPaths } from "./generated/administration";
import type { paths as AncillaryPaths } from "./generated/ancillary";
import type { paths as DiscountPaths } from "./generated/discount";
import type { paths as GatewayPaths } from "./generated/gateway";
import type { paths as InventoryPaths } from "./generated/inventory";
import type { paths as PricingPaths } from "./generated/pricing";
import type { paths as ReservationPaths } from "./generated/reservation";

/** The cookie in which the admin gateway hands out the CSRF token. */
export const CSRF_COOKIE = "ADMIN-XSRF-TOKEN";

// All clients call the address the page was loaded from. The admin gateway there signs the user
// in and passes each path on to the service that owns it.
const options = {
  baseUrl: globalThis.location?.origin ?? "",
  // Looked up on every call rather than once, so that tests can replace fetch.
  fetch: (request: Request) => globalThis.fetch(request),
};

function cookie(name: string): string | undefined {
  return document.cookie
    .split("; ")
    .find((part) => part.startsWith(`${name}=`))
    ?.slice(name.length + 1);
}

/** Requests that change data carry the CSRF token from the gateway's cookie. */
const csrf: Middleware = {
  onRequest({ request }) {
    const token = cookie(CSRF_COOKIE);
    if (token && !["GET", "HEAD", "OPTIONS"].includes(request.method)) {
      request.headers.set("X-XSRF-TOKEN", decodeURIComponent(token));
    }
    return request;
  },
};

/** Reports a lost session, so that the UI asks to sign in again. */
const signedOut: Middleware = {
  onResponse({ response }) {
    if (response.status === 401) {
      globalThis.dispatchEvent(new Event(SIGNED_OUT_EVENT));
    }
    return response;
  },
};

export const SIGNED_OUT_EVENT = "admin-ui:signed-out";

export const administration = createClient<AdministrationPaths>(options);
export const inventory = createClient<InventoryPaths>(options);
export const gateway = createClient<GatewayPaths>(options);
export const pricing = createClient<PricingPaths>(options);
export const ancillary = createClient<AncillaryPaths>(options);
export const discount = createClient<DiscountPaths>(options);
export const reservations = createClient<ReservationPaths>(options);
for (const client of [
  administration,
  inventory,
  gateway,
  pricing,
  ancillary,
  discount,
  reservations,
]) {
  client.use(csrf, signedOut);
}

/** An error answer of a service, in the Problem Details format. */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type Result<T> = { data?: T; error?: unknown; response: Response };

/** Returns the data of a successful call, or throws an ApiError with the service's explanation. */
export async function unwrap<T>(call: Promise<Result<T>>): Promise<T> {
  const { data, error, response } = await call;
  if (!response.ok) {
    const problem = (error ?? {}) as { detail?: string; title?: string };
    throw new ApiError(
      response.status,
      problem.detail ?? problem.title ?? `The request failed with status ${response.status}`,
    );
  }
  return data as T;
}
