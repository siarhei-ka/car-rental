import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { fakeApi, get, renderPage, screenWidth } from "./harness";

const countries = [
  { code: "de", name: "Germany", currency: "EUR", active: true },
  { code: "pl", name: "Poland", currency: "PLN", active: true },
];
const locations = [
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
];
const carTypes = [{ code: "SEDAN", name: "Sedan", active: true }];
const carClasses = [{ code: "ECONOMY", name: "Economy", active: true }];

const referenceRoutes = [
  get("/api/v1/admin/countries", countries),
  get("/api/v1/admin/locations", locations),
  get("/api/v1/admin/car-types", carTypes),
  get("/api/v1/admin/car-classes", carClasses),
];

beforeEach(() => screenWidth(1280));
afterEach(() => vi.unstubAllGlobals());

describe("Countries", () => {
  it("lists countries and adds a new one", async () => {
    const calls = fakeApi([
      ...referenceRoutes,
      {
        method: "PUT",
        path: "/api/v1/admin/countries/fr",
        answer: () => ({
          status: 201,
          body: { code: "fr", name: "France", currency: "EUR", active: true },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/countries");

    expect(await screen.findByRole("cell", { name: "Poland" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Add country" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Code/), "FR");
    await user.type(within(dialog).getByLabelText(/Name/), "France");
    await user.type(within(dialog).getByLabelText(/Currency/), "eur");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.path).toBe("/api/v1/admin/countries/fr");
    expect(put?.body).toEqual({ name: "France", currency: "EUR", active: true });
    expect(
      calls.filter((c) => c.path === "/api/v1/admin/countries" && c.method === "GET").length,
    ).toBeGreaterThan(1);
  });

  it("shows the explanation of the service when saving fails", async () => {
    fakeApi([
      ...referenceRoutes,
      {
        method: "PUT",
        path: "/api/v1/admin/countries/xx",
        answer: () => ({
          status: 400,
          body: {
            type: "/problems/invalid-reference-data",
            detail: "Currency is not an ISO 4217 code: ZZZ",
          },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/countries");

    await user.click(await screen.findByRole("button", { name: "Add country" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Code/), "xx");
    await user.type(within(dialog).getByLabelText(/Name/), "X");
    await user.type(within(dialog).getByLabelText(/Currency/), "zzz");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(
      await within(dialog).findByText("Currency is not an ISO 4217 code: ZZZ"),
    ).toBeInTheDocument();
  });
});

describe("Car types", () => {
  it("changes a car type", async () => {
    const calls = fakeApi([
      ...referenceRoutes,
      {
        method: "PUT",
        path: "/api/v1/admin/car-types/SEDAN",
        answer: () => ({ body: { code: "SEDAN", name: "Saloon", active: true } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/car-types");

    await user.click(await screen.findByRole("button", { name: "Edit Sedan" }));
    const dialog = await screen.findByRole("dialog");
    expect(within(dialog).getByLabelText(/Code/)).toBeDisabled();
    const name = within(dialog).getByLabelText(/Name/);
    await user.clear(name);
    await user.type(name, "Saloon");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ name: "Saloon", active: true });
  });
});

describe("Pages of one country", () => {
  it("asks to choose a country first", async () => {
    fakeApi(referenceRoutes);
    renderPage(<App />, "/locations");

    expect(await screen.findByText("Choose a country in the top bar first.")).toBeInTheDocument();
  });

  it("lists the locations of the chosen country", async () => {
    localStorage.setItem("admin-ui.country", "pl");
    const calls = fakeApi(referenceRoutes);
    renderPage(<App />, "/locations");

    expect(await screen.findByRole("cell", { name: "Warsaw Airport" })).toBeInTheDocument();
    const request = calls.find((c) => c.path === "/api/v1/admin/locations");
    expect(request?.headers.get("X-Country")).toBe("pl");
  });

  it("changes the stock of a location", async () => {
    localStorage.setItem("admin-ui.country", "pl");
    const calls = fakeApi([
      ...referenceRoutes,
      get("/api/v1/admin/stock", [
        { locationId: "krk-airport", carType: "SEDAN", carClass: "ECONOMY", cars: 5 },
      ]),
      {
        method: "PUT",
        path: "/api/v1/admin/stock/krk-airport/SEDAN/ECONOMY",
        answer: () => ({
          body: { locationId: "krk-airport", carType: "SEDAN", carClass: "ECONOMY", cars: 7 },
        }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/stock");

    await user.click(await screen.findByRole("button", { name: "Change SEDAN ECONOMY" }));
    const dialog = await screen.findByRole("dialog");
    const cars = within(dialog).getByLabelText(/Cars/);
    await user.clear(cars);
    await user.type(cars, "7");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.some((c) => c.method === "PUT")).toBe(true));
    const stockRequest = calls.find((c) => c.path === "/api/v1/admin/stock");
    // The first location by city, Krakow, is chosen at first.
    expect(stockRequest?.search).toBe("?locationId=krk-airport");
    expect(calls.find((c) => c.method === "PUT")?.body).toEqual({ cars: 7 });
  });

  it("registers the arrival of a transfer and shows times in the location's time zone", async () => {
    localStorage.setItem("admin-ui.country", "pl");
    const calls = fakeApi([
      ...referenceRoutes,
      get("/api/v1/admin/transfers", [
        {
          id: "t-1",
          locationId: "waw-airport",
          carType: "SEDAN",
          carClass: "ECONOMY",
          cars: 2,
          departure: "2026-12-05T07:00:00Z",
          targetLocationId: "krk-airport",
          plannedArrival: "2026-12-05T17:00:00Z",
        },
      ]),
      {
        method: "POST",
        path: "/api/v1/admin/transfers/t-1/arrival",
        answer: () => ({ status: 204 }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/transfers");

    expect(await screen.findByRole("cell", { name: "2026-12-05 08:00" })).toBeInTheDocument();
    expect(screen.getByRole("cell", { name: "Krakow Airport" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Register arrival" }));

    await waitFor(() =>
      expect(calls.some((c) => c.path === "/api/v1/admin/transfers/t-1/arrival")).toBe(true),
    );
  });
});

describe("Accessibility", () => {
  it("names every field of the transfer form and the country selector", async () => {
    localStorage.setItem("admin-ui.country", "pl");
    fakeApi([...referenceRoutes, get("/api/v1/admin/transfers", [])]);
    const user = userEvent.setup();
    renderPage(<App />, "/transfers");

    expect(await screen.findByRole("combobox", { name: "Country" })).toBeInTheDocument();
    await user.click(await screen.findByRole("button", { name: "Send cars" }));
    const dialog = await screen.findByRole("dialog");
    for (const name of [/From/, /To/, /Car type/, /Car class/]) {
      expect(within(dialog).getByRole("combobox", { name })).toBeInTheDocument();
    }
  });
});

describe("On a phone", () => {
  it("shows cards instead of a table and a menu button instead of a side bar", async () => {
    screenWidth(375);
    fakeApi(referenceRoutes);
    const user = userEvent.setup();
    renderPage(<App />, "/countries");

    expect(await screen.findByText("Poland")).toBeInTheDocument();
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
    expect(screen.queryByRole("navigation", { name: "Sections" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Open navigation" }));
    expect(await screen.findByRole("navigation", { name: "Sections" })).toBeInTheDocument();
  });
});
