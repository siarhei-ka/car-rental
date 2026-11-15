import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { SIGN_IN_URL, browser } from "../session";
import { fakeApi, get, renderPage, screenWidth, type Call, type Route } from "./harness";

const customer = { signedIn: true, name: "Anna Driver", roles: ["CUSTOMER"] };
const profile = { dateOfBirth: "1990-05-17", licenceCountry: "pl" };
const noProfile: Route = {
  method: "GET",
  path: "/api/v1/profile",
  answer: () => ({
    status: 404,
    body: { type: "/problems/profile-not-found", title: "No driver profile", status: 404 },
  }),
};
const search = {
  pickUpLocation: {
    id: "waw-airport",
    country: "pl",
    name: "Warsaw Airport",
    timeZone: "Europe/Warsaw",
    active: true,
  },
  returnLocation: {
    id: "waw-airport",
    country: "pl",
    name: "Warsaw Airport",
    timeZone: "Europe/Warsaw",
    active: true,
  },
  start: "2026-12-24T08:00:00Z",
  days: 3,
  end: "2026-12-27T08:00:00Z",
  offers: [
    {
      carType: "SUV",
      carTypeName: "SUV",
      carClass: "ECONOMY",
      carClassName: "Economy",
      availableCars: 1,
      dailyRate: { amount: "149.00", currency: "PLN" },
    },
  ],
};
const selection =
  "/selection?country=pl&pickUp=waw-airport&returnTo=waw-airport&start=2026-12-24T08%3A00%3A00.000Z&days=3&carType=SUV&carClass=ECONOMY";

let go: ReturnType<typeof vi.fn<(url: string) => void>>;

beforeEach(() => {
  screenWidth(1280);
  sessionStorage.clear();
  go = vi.fn<(url: string) => void>();
  vi.spyOn(browser, "go").mockImplementation(go);
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

describe("A visitor", () => {
  it("signs in from the selection page and comes back to it", async () => {
    fakeApi([get("/api/v1/search", search)]);
    const user = userEvent.setup();
    renderPage(<App />, selection);

    await user.click(
      await screen.findByRole("button", { name: "Sign in to see the price and reserve" }),
    );

    expect(go).toHaveBeenCalledWith(SIGN_IN_URL);
    expect(sessionStorage.getItem("ui:after-sign-in")).toBe(selection);
  });

  it("can sign in from every page", async () => {
    fakeApi([]);
    const user = userEvent.setup();
    renderPage(<App />, "/profile");

    expect(await screen.findByText("Sign in to enter your driver profile.")).toBeInTheDocument();
    await user.click(within(screen.getByRole("banner")).getByRole("button", { name: "Sign in" }));
    expect(go).toHaveBeenCalledWith(SIGN_IN_URL);
  });

  it("is told when the sign-in failed", async () => {
    fakeApi([
      get("/api/v1/countries", []),
      get("/api/v1/locations", []),
      get("/api/v1/car-types", []),
      get("/api/v1/car-classes", []),
    ]);
    renderPage(<App />, "/?signInFailed");

    expect(await screen.findByText("The sign-in failed. Please try again.")).toBeInTheDocument();
  });
});

describe("A customer", () => {
  it("enters the driver profile on the first sign-in, then returns to the chosen car", async () => {
    sessionStorage.setItem("ui:after-sign-in", selection);
    document.cookie = "XSRF-TOKEN=token-1";
    let saved: unknown = null;
    const calls = fakeApi([
      get("/api/v1/session", customer),
      {
        method: "GET",
        path: "/api/v1/profile",
        answer: () =>
          saved ? { body: saved } : { status: 404, body: { title: "No driver profile" } },
      },
      {
        method: "PUT",
        path: "/api/v1/profile",
        answer: (call: Call) => {
          saved = call.body;
          return { body: call.body };
        },
      },
      get("/api/v1/search", search),
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/");

    expect(await screen.findByRole("heading", { name: "Driver profile" })).toBeInTheDocument();
    expect(screen.getByText(/please enter your date of birth/)).toBeInTheDocument();

    await user.type(screen.getByRole("spinbutton", { name: "Year" }), "1990");
    await user.type(screen.getByRole("spinbutton", { name: "Month" }), "05");
    await user.type(screen.getByRole("spinbutton", { name: "Day" }), "17");
    await user.type(
      screen.getByRole("combobox", { name: /Country that issued your driving licence/ }),
      "Pola",
    );
    await user.click(await screen.findByRole("option", { name: "Poland" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByRole("heading", { name: "SUV · Economy" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "See the price" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/price\?.*carType=SUV&carClass=ECONOMY/),
    );
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.body).toEqual({ dateOfBirth: "1990-05-17", licenceCountry: "pl" });
    expect(put?.headers.get("X-XSRF-TOKEN")).toBe("token-1");
    expect(sessionStorage.getItem("ui:after-sign-in")).toBeNull();
  });

  it("returns straight to the chosen car when the profile exists", async () => {
    sessionStorage.setItem("ui:after-sign-in", selection);
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      get("/api/v1/search", search),
    ]);
    renderPage(<App />, "/");

    expect(await screen.findByRole("heading", { name: "SUV · Economy" })).toBeInTheDocument();
    expect(await screen.findByRole("link", { name: "See the price" })).toHaveAttribute(
      "href",
      expect.stringMatching(/^\/price\?.*carType=SUV&carClass=ECONOMY/),
    );
  });

  it("is asked for the profile on the selection page when it is missing", async () => {
    fakeApi([get("/api/v1/session", customer), noProfile, get("/api/v1/search", search)]);
    renderPage(<App />, selection);

    const link = await screen.findByRole("link", { name: "Enter your driver profile" });
    expect(link).toHaveAttribute("href", `/profile?next=${encodeURIComponent(selection)}`);
  });

  it("changes the profile and sees the explanation of a refusal", async () => {
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      {
        method: "PUT",
        path: "/api/v1/profile",
        answer: () => ({
          status: 400,
          body: { title: "Invalid driver profile", detail: "A driver is at least 18 years old" },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/profile");

    expect(
      await screen.findByRole("combobox", { name: /Country that issued your driving licence/ }),
    ).toHaveValue("Poland");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("A driver is at least 18 years old")).toBeInTheDocument();
  });

  it("sees the name and signs out through Keycloak", async () => {
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      {
        method: "POST",
        path: "/logout",
        answer: () => ({ body: { redirectTo: "http://localhost:8180/logout?x=1" } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/profile");

    expect(await screen.findByRole("link", { name: "Driver profile" })).toHaveTextContent(
      "Anna Driver",
    );
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    await waitFor(() => expect(go).toHaveBeenCalledWith("http://localhost:8180/logout?x=1"));
  });
});

describe("An administrator in the customer UI", () => {
  it("is told that the account is not a customer account", async () => {
    fakeApi([
      get("/api/v1/session", { signedIn: true, name: "Anna Admin", roles: ["SUPER_ADMIN"] }),
      get("/api/v1/search", search),
    ]);
    renderPage(<App />, selection);

    expect(await screen.findByText(/Anna Admin is not a customer account/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Driver profile" })).not.toBeInTheDocument();
  });
});
