import { act, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { browser } from "../session";
import { countryAdmin, fakeApi, get, renderPage, screenWidth } from "./harness";

const countries = [
  { code: "de", name: "Germany", currency: "EUR", active: true },
  { code: "pl", name: "Poland", currency: "PLN", active: true },
];
const carTypes = [{ code: "SEDAN", name: "Sedan", active: true }];

let go: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  screenWidth(1280);
  go = vi.spyOn(browser, "go").mockImplementation(() => {});
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.cookie = "ADMIN-XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  localStorage.clear();
});

describe("Sign-in", () => {
  it("asks a visitor to sign in and starts the sign-in at the gateway", async () => {
    const calls = fakeApi([get("/api/v1/session", { signedIn: false, roles: [] })]);
    const user = userEvent.setup();
    renderPage(<App />, "/stock");

    await user.click(await screen.findByRole("button", { name: "Sign in" }));

    expect(go).toHaveBeenCalledWith("/oauth2/authorization/keycloak");
    expect(calls.map((c) => c.path)).toEqual(["/api/v1/session"]);
  });

  it("refuses an account that is not an administrator", async () => {
    fakeApi([
      get("/api/v1/session", { signedIn: true, name: "Carla", roles: ["CUSTOMER"], country: null }),
    ]);
    renderPage(<App />, "/stock");

    expect(await screen.findByText(/Carla is not an administrator account/)).toBeInTheDocument();
    expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
  });

  it("asks to sign in again when the session is lost", async () => {
    let signedIn = true;
    fakeApi([
      {
        method: "GET",
        path: "/api/v1/session",
        answer: () => ({
          body: signedIn
            ? { signedIn: true, name: "Anna", roles: ["SUPER_ADMIN"], country: null }
            : { signedIn: false, roles: [] },
        }),
      },
      get("/api/v1/admin/countries", countries),
      {
        method: "GET",
        path: "/api/v1/admin/car-types",
        answer: () => {
          signedIn = false;
          return { status: 401, body: { title: "Not signed in" } };
        },
      },
    ]);
    renderPage(<App />, "/car-types");

    expect(await screen.findByRole("button", { name: "Sign in" })).toBeInTheDocument();
  });
});

describe("Sign-out", () => {
  it("signs out at the gateway with the CSRF token and continues to Keycloak", async () => {
    document.cookie = "ADMIN-XSRF-TOKEN=token-1";
    const calls = fakeApi([
      get("/api/v1/admin/countries", countries),
      get("/api/v1/admin/car-types", carTypes),
      {
        method: "POST",
        path: "/logout",
        answer: () => ({ body: { redirectTo: "http://localhost:8180/logout?x=1" } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/car-types");

    expect(await screen.findByText("Super Administrator")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Sign out" }));

    await waitFor(() => expect(go).toHaveBeenCalledWith("http://localhost:8180/logout?x=1"));
    const logout = calls.find((c) => c.path === "/logout");
    expect(logout?.headers.get("X-XSRF-TOKEN")).toBe("token-1");
  });
});

describe("Country administrator", () => {
  it("works only in their own country and cannot change shared data", async () => {
    localStorage.setItem("admin-ui.country", "de");
    const calls = fakeApi([
      get("/api/v1/session", countryAdmin("pl")),
      get("/api/v1/admin/countries", countries),
      get("/api/v1/admin/car-types", carTypes),
      get("/api/v1/admin/locations", []),
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/car-types");

    expect(await screen.findByRole("cell", { name: "Sedan" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add car type" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit Sedan" })).not.toBeInTheDocument();
    expect(screen.queryByRole("combobox", { name: "Country" })).not.toBeInTheDocument();
    expect(await screen.findByLabelText("Country")).toHaveTextContent("Poland");

    await user.click(screen.getByRole("link", { name: "Locations" }));
    await waitFor(() =>
      expect(calls.find((c) => c.path === "/api/v1/admin/locations")).toBeDefined(),
    );
    expect(calls.find((c) => c.path === "/api/v1/admin/locations")?.headers.get("X-Country")).toBe(
      "pl",
    );
  });

  it("sends no CSRF header with reads", async () => {
    document.cookie = "ADMIN-XSRF-TOKEN=token-1";
    const calls = fakeApi([
      get("/api/v1/admin/countries", countries),
      get("/api/v1/admin/car-types", carTypes),
    ]);
    renderPage(<App />, "/car-types");
    await act(async () => {
      await screen.findByRole("cell", { name: "Sedan" });
    });
    expect(calls.every((c) => !c.headers.has("X-XSRF-TOKEN"))).toBe(true);
  });
});
