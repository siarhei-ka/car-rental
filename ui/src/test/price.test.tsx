import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { browser } from "../session";
import { fakeApi, get, renderPage, screenWidth, type Route } from "./harness";

const customer = { signedIn: true, name: "Anna Driver", roles: ["CUSTOMER"] };
const profile = { dateOfBirth: "1990-05-17", licenceCountry: "pl" };
const pln = (amount: string) => ({ amount, currency: "PLN" });
const price =
  "/price?country=pl&pickUp=waw-airport&returnTo=krk-airport&start=2026-12-24T08%3A00%3A00.000Z" +
  "&days=3&carType=SUV&carClass=ECONOMY&insurance=FULL&extras=CHILD_SEAT%3A2";

const quote = {
  currency: "PLN",
  lines: [
    {
      kind: "CAR_RENTAL",
      code: "SUV/ECONOMY",
      name: "Car rental",
      quantity: 1,
      unitPrice: pln("149.00"),
      charge: "PER_DAY",
      amount: pln("447.00"),
    },
    {
      kind: "INSURANCE",
      code: "FULL",
      name: "Full cover",
      quantity: 1,
      unitPrice: pln("50.00"),
      charge: "PER_DAY",
      amount: pln("150.00"),
    },
    {
      kind: "EXTRA",
      code: "CHILD_SEAT",
      name: "Child seat",
      quantity: 2,
      unitPrice: pln("25.00"),
      charge: "PER_DAY",
      amount: pln("150.00"),
    },
    {
      kind: "ONE_WAY_FEE",
      code: null,
      name: "One-way fee",
      quantity: 1,
      unitPrice: pln("150.00"),
      charge: "PER_RENTAL",
      amount: pln("150.00"),
    },
  ],
  subtotal: pln("897.00"),
  discount: { name: "A week or longer: 5% off", amount: pln("44.85") },
  total: pln("852.15"),
};

const quoteRoute = (answer: Route["answer"]): Route => ({
  method: "POST",
  path: "/api/v1/quotes",
  answer,
});

beforeEach(() => {
  screenWidth(1280);
  document.cookie = "XSRF-TOKEN=token-2";
});
afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("Final price", () => {
  it("shows every line, the discount and the total for the chosen insurance and extras", async () => {
    const calls = fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      quoteRoute(() => ({ body: quote })),
    ]);
    renderPage(<App />, price);

    const lines = await screen.findByLabelText("Price");
    expect(within(lines).getByText(/^Car rental, PLN\s149\.00 per day$/)).toBeInTheDocument();
    expect(within(lines).getByText(/^Full cover, PLN\s50\.00 per day$/)).toBeInTheDocument();
    expect(within(lines).getByText(/^Child seat × 2, PLN\s25\.00 per day$/)).toBeInTheDocument();
    expect(within(lines).getByText("One-way fee")).toBeInTheDocument();
    expect(within(lines).getByText(/^PLN\s897\.00$/)).toBeInTheDocument();
    expect(within(lines).getByText("Discount: A week or longer: 5% off")).toBeInTheDocument();
    expect(within(lines).getByText(/^−PLN\s44\.85$/)).toBeInTheDocument();
    expect(within(lines).getByText("Total, due at pick-up")).toBeInTheDocument();
    expect(within(lines).getByText(/^PLN\s852\.15$/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reserve, pay at pick-up" })).toBeEnabled();

    const post = calls.find((c) => c.path === "/api/v1/quotes");
    expect(post?.headers.get("X-Country")).toBe("pl");
    expect(post?.headers.get("X-XSRF-TOKEN")).toBe("token-2");
    expect(post?.body).toEqual({
      pickUpLocationId: "waw-airport",
      returnLocationId: "krk-airport",
      start: "2026-12-24T08:00:00.000Z",
      days: 3,
      carType: "SUV",
      carClass: "ECONOMY",
      insurance: "FULL",
      extras: [{ code: "CHILD_SEAT", quantity: 2 }],
    });
    expect(
      screen.getByRole("link", { name: "Change insurance or extras" }).getAttribute("href"),
    ).toMatch(/^\/selection\?.*insurance=FULL&extras=CHILD_SEAT%3A2/);
  });

  it("offers to try again when the price is not available", async () => {
    let attempts = 0;
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      quoteRoute(() =>
        ++attempts === 1
          ? { status: 503, body: { detail: "The insurance service is not available" } }
          : { body: { ...quote, discount: null, total: quote.subtotal } },
      ),
    ]);
    const user = userEvent.setup();
    renderPage(<App />, price);

    expect(
      await screen.findByText(
        "The price is not available right now. Please try again in a moment.",
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(await screen.findByText("Total, due at pick-up")).toBeInTheDocument();
    expect(screen.queryByText(/^Discount/)).not.toBeInTheDocument();
    await waitFor(() => expect(attempts).toBe(2));
  });

  it("explains why a rental cannot be priced", async () => {
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", { ...profile, dateOfBirth: "2006-01-01" }),
      quoteRoute(() => ({
        status: 422,
        body: {
          type: "/problems/driver-too-young",
          detail: "The driver must be at least 21 years old on the start of the rental",
        },
      })),
    ]);
    renderPage(<App />, price);

    expect(
      await screen.findByText(/The driver must be at least 21 years old on the start/),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Change your choice" })).toBeInTheDocument();
  });

  it("asks a visitor to sign in and comes back to the price", async () => {
    const go = vi.fn<(url: string) => void>();
    vi.spyOn(browser, "go").mockImplementation(go);
    const calls = fakeApi([]);
    const user = userEvent.setup();
    renderPage(<App />, price);

    await user.click(await screen.findByRole("button", { name: "Sign in to see the price" }));

    expect(sessionStorage.getItem("ui:after-sign-in")).toBe(price);
    expect(go).toHaveBeenCalled();
    expect(calls.some((c) => c.path === "/api/v1/quotes")).toBe(false);
  });
});

const reservation = {
  id: "5b3c3f5e-8d1a-4f43-9b7e-2f8f6c1b9a10",
  country: "pl",
  customerId: "customer-1",
  pickUpLocationId: "waw-airport",
  returnLocationId: "krk-airport",
  carType: "SUV",
  carClass: "ECONOMY",
  start: "2026-12-24T08:00:00Z",
  days: 3,
  end: "2026-12-27T08:00:00Z",
  oneWay: true,
  insurance: "FULL",
  extras: [{ code: "CHILD_SEAT", quantity: 2 }],
  price: quote,
  state: "CONFIRMED",
  paymentMethod: "PAY_AT_PICK_UP",
  inventoryUpToDate: true,
};
const names = [
  get("/api/v1/locations", []),
  get("/api/v1/car-types", [{ code: "SUV", name: "SUV", active: true }]),
  get("/api/v1/car-classes", [{ code: "ECONOMY", name: "Economy", active: true }]),
];

describe("Reserving", () => {
  it("reserves at the price shown and confirms the amount due at pick-up", async () => {
    const calls = fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      quoteRoute(() => ({ body: quote })),
      {
        method: "POST",
        path: "/api/v1/reservations",
        answer: () => ({ status: 201, body: reservation }),
      },
      get(`/api/v1/reservations/${reservation.id}`, reservation),
      ...names,
    ]);
    const user = userEvent.setup();
    renderPage(<App />, price);

    await user.click(await screen.findByRole("button", { name: "Reserve, pay at pick-up" }));

    expect(
      await screen.findByRole("heading", { name: "Your car is reserved" }),
    ).toBeInTheDocument();
    expect(
      await screen.findByText(/^Pay PLN\s852\.15 when you pick up the car\.$/),
    ).toBeInTheDocument();
    expect(await screen.findByRole("heading", { name: "SUV · Economy" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Cancel this reservation" })).toBeInTheDocument();

    const post = calls.find((c) => c.method === "POST" && c.path === "/api/v1/reservations");
    expect(post?.headers.get("X-Country")).toBe("pl");
    expect(post?.headers.get("X-XSRF-TOKEN")).toBe("token-2");
    expect(post?.headers.get("Idempotency-Key")).toMatch(/^[0-9a-f-]{36}$/);
    expect(post?.body).toMatchObject({
      pickUpLocationId: "waw-airport",
      returnLocationId: "krk-airport",
      insurance: "FULL",
      extras: [{ code: "CHILD_SEAT", quantity: 2 }],
      expectedTotal: { amount: "852.15", currency: "PLN" },
    });
  });

  it("shows a changed price and reserves only when the customer confirms it", async () => {
    const changed = { ...quote, discount: null, total: pln("897.00") };
    let attempts = 0;
    const calls = fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      quoteRoute(() => ({ body: quote })),
      {
        method: "POST",
        path: "/api/v1/reservations",
        answer: () =>
          ++attempts === 1
            ? {
                status: 409,
                body: {
                  type: "/problems/price-changed",
                  detail: "The price has changed to 897.00 PLN",
                  price: changed,
                },
              }
            : { status: 201, body: { ...reservation, price: changed } },
      },
      get(`/api/v1/reservations/${reservation.id}`, { ...reservation, price: changed }),
      ...names,
    ]);
    const user = userEvent.setup();
    renderPage(<App />, price);

    await user.click(await screen.findByRole("button", { name: "Reserve, pay at pick-up" }));

    expect(await screen.findByText(/The price has changed since you saw it/)).toBeInTheDocument();
    const lines = screen.getByLabelText("Price");
    expect(within(lines).getByText(/^PLN\s897\.00$/)).toBeInTheDocument();
    expect(within(lines).queryByText(/^Discount/)).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Reserve, pay at pick-up" }));
    expect(
      await screen.findByRole("heading", { name: "Your car is reserved" }),
    ).toBeInTheDocument();

    const posts = calls.filter((c) => c.method === "POST" && c.path === "/api/v1/reservations");
    expect(posts).toHaveLength(2);
    expect((posts[1]!.body as { expectedTotal: unknown }).expectedTotal).toEqual(pln("897.00"));
    expect(posts[1]!.headers.get("Idempotency-Key")).not.toBe(
      posts[0]!.headers.get("Idempotency-Key"),
    );
  });

  it("explains why a car could not be reserved", async () => {
    fakeApi([
      get("/api/v1/session", customer),
      get("/api/v1/profile", profile),
      quoteRoute(() => ({ body: quote })),
      {
        method: "POST",
        path: "/api/v1/reservations",
        answer: () => ({
          status: 409,
          body: {
            type: "/problems/no-car-available",
            detail: "No car of this type is free for the whole rental",
          },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, price);

    await user.click(await screen.findByRole("button", { name: "Reserve, pay at pick-up" }));

    expect(
      await screen.findByText("No car of this type is free for the whole rental"),
    ).toBeInTheDocument();
  });
});
