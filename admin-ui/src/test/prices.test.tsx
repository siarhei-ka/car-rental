import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { fakeApi, get, renderPage, screenWidth } from "./harness";

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
  ]),
  get("/api/v1/admin/car-types", [
    { code: "SEDAN", name: "Sedan", active: true },
    { code: "SUV", name: "SUV", active: true },
    { code: "OLD", name: "Old", active: false },
  ]),
  get("/api/v1/admin/car-classes", [{ code: "ECONOMY", name: "Economy", active: true }]),
];
const pln = (amount: string) => ({ amount, currency: "PLN" });

beforeEach(() => {
  screenWidth(1280);
  localStorage.setItem("admin-ui.country", "pl");
});
afterEach(() => vi.unstubAllGlobals());

describe("Daily rates", () => {
  it("shows which cars have no rate and sets one", async () => {
    const calls = fakeApi([
      ...references,
      get("/api/v1/admin/rates", [
        {
          locationId: "waw-airport",
          carType: "SEDAN",
          carClass: "ECONOMY",
          dailyRate: pln("149.00"),
        },
      ]),
      {
        method: "PUT",
        path: "/api/v1/admin/rates/waw-airport/SUV/ECONOMY",
        answer: (call) => ({
          body: {
            locationId: "waw-airport",
            carType: "SUV",
            carClass: "ECONOMY",
            dailyRate: { ...(call.body as object), currency: "PLN" },
          },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/rates");

    const sedan = (await screen.findByRole("cell", { name: "Sedan" })).closest("tr")!;
    expect(within(sedan).getByText("PLN 149.00")).toBeInTheDocument();
    const suv = screen.getByRole("cell", { name: "SUV" }).closest("tr")!;
    expect(within(suv).getByText("No rate: not offered")).toBeInTheDocument();
    expect(screen.queryByRole("cell", { name: "Old" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Set rate of SUV Economy" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Price per day/), "219,50");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.body).toEqual({ amount: "219.50" });
    expect(put?.headers.get("X-Country")).toBe("pl");
    expect(calls.find((c) => c.path === "/api/v1/admin/rates")?.search).toBe(
      "?locationId=waw-airport",
    );
  });

  it("shows the explanation of a refused rate", async () => {
    fakeApi([
      ...references,
      get("/api/v1/admin/rates", []),
      {
        method: "PUT",
        path: "/api/v1/admin/rates/waw-airport/SEDAN/ECONOMY",
        answer: () => ({ status: 400, body: { detail: "A daily rate is more than zero" } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/rates");

    await user.click(await screen.findByRole("button", { name: "Set rate of Sedan Economy" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Price per day/), "0");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByText("A daily rate is more than zero")).toBeInTheDocument();
  });
});

describe("Extras", () => {
  const childSeat = {
    code: "CHILD_SEAT",
    name: "Child seat",
    kind: "EQUIPMENT",
    charge: "PER_DAY",
    price: pln("25.00"),
    maxQuantity: 3,
    active: true,
  };

  it("lists extras with their prices and adds one", async () => {
    const calls = fakeApi([
      ...references,
      get("/api/v1/admin/extras", [childSeat]),
      get("/api/v1/admin/one-way-fee", pln("150.00")),
      {
        method: "PUT",
        path: "/api/v1/admin/extras/ROAD_TOLL",
        answer: () => ({ body: { ...childSeat, code: "ROAD_TOLL" } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/extras");

    const seat = (await screen.findByRole("cell", { name: "Child seat" })).closest("tr")!;
    expect(within(seat).getByText("PLN 25.00 per day")).toBeInTheDocument();
    expect(
      screen.getByText("PLN 150.00 when the car is returned to another location"),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Add extra" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Code/), "road_toll");
    await user.type(within(dialog).getByLabelText(/Name/), "Road toll payment");
    await user.click(within(dialog).getByLabelText(/Kind/));
    await user.click(await screen.findByRole("option", { name: "Service" }));
    await user.click(within(dialog).getByLabelText(/Charged/));
    await user.click(await screen.findByRole("option", { name: "Per rental" }));
    await user.type(within(dialog).getByLabelText(/^Price/), "49");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({
      name: "Road toll payment",
      kind: "SERVICE",
      charge: "PER_RENTAL",
      price: "49",
      maxQuantity: 1,
      active: true,
    });
  });

  it("sets a missing one-way fee", async () => {
    const calls = fakeApi([
      ...references,
      get("/api/v1/admin/extras", []),
      {
        method: "GET",
        path: "/api/v1/admin/one-way-fee",
        answer: () => ({ status: 404, body: { title: "No one-way fee" } }),
      },
      { method: "PUT", path: "/api/v1/admin/one-way-fee", answer: () => ({ body: pln("120.00") }) },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/extras");

    expect(
      await screen.findByText("Not set: one-way rentals cannot be priced"),
    ).toBeInTheDocument();
    expect(screen.getByText("No extras yet.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Change one-way fee" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Fee/), "120");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ amount: "120" });
  });
});
