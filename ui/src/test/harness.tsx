import { QueryClient } from "@tanstack/react-query";
import { render } from "@testing-library/react";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router";
import { vi } from "vitest";
import { Providers } from "../providers";

export type Call = {
  method: string;
  path: string;
  search: string;
  headers: Headers;
  body: unknown;
};
type Answer = { status?: number; body?: unknown };
export type Route = { method: string; path: string; answer: (call: Call) => Answer };

/**
 * Replaces fetch with a fake API. Each route answers a method and path. Every call is recorded,
 * and a call without a route fails the test.
 */
export function fakeApi(given: Route[]) {
  const calls: Call[] = [];
  // A visitor who has not signed in, unless a test gives a session of its own.
  const routes = [...given, get("/api/v1/session", { signedIn: false, roles: [] })];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (input: Request) => {
      const url = new URL(input.url);
      const text = await input.text();
      const call: Call = {
        method: input.method,
        path: url.pathname,
        search: url.search,
        headers: input.headers,
        body: text ? JSON.parse(text) : undefined,
      };
      calls.push(call);
      const route = routes.find((r) => r.method === call.method && r.path === call.path);
      if (!route) {
        throw new Error(`No fake answer for ${call.method} ${call.path}`);
      }
      const { status = 200, body } = route.answer(call);
      return new Response(body === undefined ? null : JSON.stringify(body), {
        status,
        headers: {
          "Content-Type": status >= 400 ? "application/problem+json" : "application/json",
        },
      });
    }),
  );
  return calls;
}

export const get = (path: string, body: unknown): Route => ({
  method: "GET",
  path,
  answer: () => ({ body }),
});

/** Makes media queries answer as on a screen of the given width. */
export function screenWidth(width: number) {
  vi.stubGlobal("matchMedia", (query: string) => {
    const min = /min-width:\s*([\d.]+)px/.exec(query);
    const max = /max-width:\s*([\d.]+)px/.exec(query);
    const matches = (!min || width >= Number(min[1])) && (!max || width <= Number(max[1]));
    return {
      matches,
      media: query,
      onchange: null,
      addListener: () => {},
      removeListener: () => {},
      addEventListener: () => {},
      removeEventListener: () => {},
      dispatchEvent: () => false,
    };
  });
}

export function renderPage(page: ReactNode, path = "/") {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Providers queryClient={queryClient}>
      <MemoryRouter initialEntries={[path]}>{page}</MemoryRouter>
    </Providers>,
  );
}
