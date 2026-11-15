import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { fakeApi, get, renderPage, screenWidth } from "./harness";

const pln = (amount: string) => ({ amount, currency: "PLN" });
const references = [
  get("/api/v1/admin/countries", [{ code: "pl", name: "Poland", currency: "PLN", active: true }]),
  get("/api/v1/admin/locations", [
    {
      id: "waw-airport",
      country: "pl",
      name: "Warsaw Airport",
      city: "Warsaw",
      latitude: 52.17,
      longitude: 20.97,
      timeZone: "Europe/Warsaw",
      active: true,
    },
    {
      id: "krk-airport",
      country: "pl",
      name: "Krakow Airport",
      city: "Krakow",
      latitude: 50.08,
      longitude: 19.78,
      timeZone: "Europe/Warsaw",
      active: true,
    },
  ]),
  get("/api/v1/admin/car-types", [{ code: "SEDAN", name: "Sedan", active: true }]),
  get("/api/v1/admin/car-classes", [{ code: "ECONOMY", name: "Economy", active: true }]),
];
const reservation = (id: string, start: string, state: string, returnTo = "waw-airport") => ({
  id,
  country: "pl",
  customerId: "customer-1",
  pickUpLocationId: "waw-airport",
  returnLocationId: returnTo,
  carType: "SEDAN",
  carClass: "ECONOMY",
  start,
  days: 3,
  end: start,
  oneWay: returnTo !== "waw-airport",
  insurance: "BASIC",
  extras: [],
  price: {
    currency: "PLN",
    lines: [],
    subtotal: pln("477.00"),
    discount: null,
    total: pln("477.00"),
  },
  state,
  paymentMethod: "PAY_AT_PICK_UP",
  inventoryUpToDate: true,
});
const future = reservation(
  "11111111-1111-4111-8111-111111111111",
  "2099-12-24T08:00:00Z",
  "CONFIRMED",
  "krk-airport",
);
const started = reservation(
  "22222222-2222-4222-8222-222222222222",
  "2026-01-10T08:00:00Z",
  "CONFIRMED",
);
const pickedUp = reservation(
  "33333333-3333-4333-8333-333333333333",
  "2026-01-11T08:00:00Z",
  "PICKED_UP",
);

beforeEach(() => {
  screenWidth(1280);
  localStorage.setItem("admin-ui.country", "pl");
});
afterEach(() => vi.unstubAllGlobals());

describe("Reservations", () => {
  it("lists the reservations of the country with what staff can register", async () => {
    fakeApi([...references, get("/api/v1/admin/reservations", [future, started, pickedUp])]);
    renderPage(<App />, "/reservations");

    const first = (
      await screen.findByRole("cell", { name: "Warsaw Airport → Krakow Airport" })
    ).closest("tr")!;
    expect(within(first).getByText("Sedan · Economy")).toBeInTheDocument();
    expect(within(first).getByText("2099-12-24 09:00 · 3 d")).toBeInTheDocument();
    expect(within(first).getByText(/^PLN\s477\.00$/)).toBeInTheDocument();
    expect(within(first).getByText("Confirmed")).toBeInTheDocument();
    expect(within(first).getByRole("button", { name: /^Pick up/ })).toBeInTheDocument();
    expect(within(first).queryByRole("button", { name: /^No-show/ })).not.toBeInTheDocument();

    expect(
      screen.getByRole("button", { name: `No-show: reservation ${started.id}` }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: `Return: reservation ${pickedUp.id}` }),
    ).toBeInTheDocument();
  });

  it("registers a pick-up in the administrator's country and filters by location", async () => {
    const calls = fakeApi([
      ...references,
      get("/api/v1/admin/reservations", [future]),
      {
        method: "POST",
        path: `/api/v1/admin/reservations/${future.id}/pick-up`,
        answer: () => ({ body: { ...future, state: "PICKED_UP" } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/reservations");

    await user.click(
      await screen.findByRole("button", { name: `Pick up: reservation ${future.id}` }),
    );
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.headers.get("X-Country")).toBe("pl");

    await user.click(screen.getByRole("combobox", { name: "Pick-up location" }));
    await user.click(await screen.findByRole("option", { name: "Krakow Airport" }));
    await waitFor(() =>
      expect(calls.some((c) => c.search === "?locationId=krk-airport")).toBe(true),
    );
  });

  it("shows the explanation of a refused change", async () => {
    fakeApi([
      ...references,
      get("/api/v1/admin/reservations", [started]),
      {
        method: "POST",
        path: `/api/v1/admin/reservations/${started.id}/no-show`,
        answer: () => ({
          status: 409,
          body: { detail: "A reservation in state CANCELLED cannot be marked as a no-show" },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/reservations");

    await user.click(
      await screen.findByRole("button", { name: `No-show: reservation ${started.id}` }),
    );
    expect(
      await screen.findByText("A reservation in state CANCELLED cannot be marked as a no-show"),
    ).toBeInTheDocument();
  });
});
