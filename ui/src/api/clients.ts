import createClient, { type Middleware } from "openapi-fetch";
import type { paths as AncillaryPaths } from "./generated/ancillary";
import type { paths as CarTypePaths } from "./generated/car-type";
import type { paths as CustomerPaths } from "./generated/customer";
import type { paths as GatewayPaths } from "./generated/gateway";
import type { paths as InsurancePaths } from "./generated/insurance";
import type { paths as LocationPaths } from "./generated/location";
import type { paths as PricingPaths } from "./generated/pricing";
import type { paths as ReservationPaths } from "./generated/reservation";
import type { paths as SearchPaths } from "./generated/search";

/** The cookie in which the customer gateway hands out the CSRF token. */
export const CSRF_COOKIE = "XSRF-TOKEN";

// All clients call the address the page was loaded from. The customer gateway there passes each
// path on to the service that owns it.
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

/** Reports a lost session, so that the UI shows the customer as signed out. */
const signedOut: Middleware = {
  onResponse({ response }) {
    if (response.status === 401) {
      globalThis.dispatchEvent(new Event(SIGNED_OUT_EVENT));
    }
    return response;
  },
};

export const SIGNED_OUT_EVENT = "ui:signed-out";

export const locationInfo = createClient<LocationPaths>(options);
export const carTypeInfo = createClient<CarTypePaths>(options);
export const searchApi = createClient<SearchPaths>(options);
export const gateway = createClient<GatewayPaths>(options);
export const customerApi = createClient<CustomerPaths>(options);
export const insuranceApi = createClient<InsurancePaths>(options);
export const ancillaryApi = createClient<AncillaryPaths>(options);
export const pricingApi = createClient<PricingPaths>(options);
export const reservationApi = createClient<ReservationPaths>(options);
for (const client of [gateway, customerApi, pricingApi, reservationApi]) {
  client.use(csrf, signedOut);
}

/** An error answer of a service, in the Problem Details format. */
export class ApiError extends Error {
  readonly status: number;
  /** The whole answer, with the problem type and any further properties. */
  readonly problem: Record<string, unknown>;

  constructor(status: number, message: string, problem: Record<string, unknown> = {}) {
    super(message);
    this.status = status;
    this.problem = problem;
  }

  /** The last part of the problem type, such as "price-changed". */
  get type(): string | undefined {
    const type = this.problem.type;
    return typeof type === "string" ? type.slice(type.lastIndexOf("/") + 1) : undefined;
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
      typeof error === "object" && error !== null ? (error as Record<string, unknown>) : {},
    );
  }
  return data as T;
}
