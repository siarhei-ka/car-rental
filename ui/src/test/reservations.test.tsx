import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { fakeApi, get, renderPage, screenWidth } from "./harness";

const customer = { signedIn: true, name: "Anna Driver", roles: ["CUSTOMER"] };
const pln = (amount: string) => ({ amount, currency: "PLN" });
const price = {
  currency: "PLN",
  lines: [
    {
      kind: "CAR_RENTAL",
      code: "SEDAN/ECONOMY",
      name: "Car rental",
      quantity: 1,
      unitPrice: pln("149.00"),
      charge: "PER_DAY",
      amount: pln("298.00"),
    },
    {
      kind: "INSURANCE",
      code: "BASIC",
      name: "Basic cover",
      quantity: 1,
      unitPrice: pln("10.00"),
      charge: "PER_DAY",
      amount: pln("20.00"),
    },
  ],
  subtotal: pln("318.00"),
  discount: null,
  total: pln("318.00"),
};
const reservation = (id: string, start: string, state: string) => ({
  id,
  country: "pl",
  customerId: "customer-1",
  pickUpLocationId: "waw-airport",
  returnLocationId: "waw-airport",
  carType: "SEDAN",
  carClass: "ECONOMY",
  start,
  days: 2,
  end: start,
  oneWay: false,
  insurance: "BASIC",
  extras: [],
  price,
  state,
  paymentMethod: "PAY_AT_PICK_UP",
  inventoryUpToDate: true,
});
const upcoming = reservation(
  "11111111-1111-4111-8111-111111111111",
  "2099-12-24T08:00:00Z",
  "CONFIRMED",
);
const past = reservation(
  "22222222-2222-4222-8222-222222222222",
  "2026-01-10T08:00:00Z",
  "RETURNED",
);
const names = [
  get("/api/v1/locations", [
    {
      id: "waw-airport",
      country: "pl",
      name: "Warsaw Chopin Airport",
      city: "Warsaw",
      latitude: 0,
      longitude: 0,
      timeZone: "Europe/Warsaw",
      active: true,
    },
  ]),
  get("/api/v1/car-types", [{ code: "SEDAN", name: "Sedan", active: true }]),
  get("/api/v1/car-classes", [{ code: "ECONOMY", name: "Economy", active: true }]),
];

beforeEach(() => {
  screenWidth(1280);
  document.cookie = "XSRF-TOKEN=token-3";
});
afterEach(() => vi.unstubAllGlobals());

describe("Your reservations", () => {
  it("lists the customer's reservations with their state and amount due", async () => {
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/reservations", [upcoming, past]),
      ...names,
    ]);
    renderPage(<App />, "/reservations");

    expect(await screen.findByRole("heading", { name: "Your reservations" })).toBeInTheDocument();
    const cards = await screen.findAllByRole("heading", { name: "Sedan · Economy" });
    expect(cards).toHaveLength(2);
    expect(screen.getByText("Confirmed")).toBeInTheDocument();
    expect(screen.getByText("Returned")).toBeInTheDocument();
    expect(screen.getAllByText(/^PLN\s318\.00, due at pick-up$/)).toHaveLength(2);
    expect(screen.getAllByText("Warsaw Chopin Airport")).toHaveLength(2);
    expect(screen.getAllByRole("link", { name: /^View reservation/ })[0]).toHaveAttribute(
      "href",
      `/reservations/pl/${upcoming.id}`,
    );
    expect(screen.getByRole("link", { name: "Your reservations" })).toBeInTheDocument();
  });

  it("says so when there are none", async () => {
    fakeApi([get("/api/v1/session", customer), get("/api/v1/reservations", []), ...names]);
    renderPage(<App />, "/reservations");

    expect(await screen.findByText(/You have no reservations yet/)).toBeInTheDocument();
  });

  it("cancels an upcoming reservation", async () => {
    const calls = fakeApi([
      get("/api/v1/session", customer),
      get(`/api/v1/reservations/${upcoming.id}`, upcoming),
      {
        method: "POST",
        path: `/api/v1/reservations/${upcoming.id}/cancel`,
        answer: () => ({ body: { ...upcoming, state: "CANCELLED" } }),
      },
      get("/api/v1/reservations", []),
      ...names,
    ]);
    const user = userEvent.setup();
    renderPage(<App />, `/reservations/pl/${upcoming.id}`);

    expect(await screen.findByRole("heading", { name: "Your reservation" })).toBeInTheDocument();
    const lines = await screen.findByLabelText("Price");
    expect(within(lines).getByText(/^Basic cover, PLN\s10\.00 per day$/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cancel this reservation" }));

    expect(await screen.findByText("Cancelled")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel this reservation" }),
    ).not.toBeInTheDocument();
    await waitFor(() => {
      const cancel = calls.find((c) => c.path.endsWith("/cancel"));
      expect(cancel?.headers.get("X-Country")).toBe("pl");
      expect(cancel?.headers.get("X-XSRF-TOKEN")).toBe("token-3");
    });
  });

  it("offers no cancellation once the rental has started or ended", async () => {
    fakeApi([
      get("/api/v1/session", customer),
      get(`/api/v1/reservations/${past.id}`, past),
      ...names,
    ]);
    renderPage(<App />, `/reservations/pl/${past.id}`);

    expect(await screen.findByText("Returned")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Cancel this reservation" }),
    ).not.toBeInTheDocument();
  });
});
