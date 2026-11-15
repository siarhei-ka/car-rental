import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { fakeApi, get, renderPage, screenWidth, type Call } from "./harness";

const countries = [
  { code: "de", name: "Germany", currency: "EUR", active: true },
  { code: "pl", name: "Poland", currency: "PLN", active: true },
];
const location = (id: string, country: string, name: string, timeZone: string) => ({
  id,
  country,
  name,
  city: name.split(" ")[0],
  latitude: 0,
  longitude: 0,
  timeZone,
  active: true,
});
const locations = [
  location("ber-airport", "de", "Berlin Airport", "Europe/Berlin"),
  location("krk-airport", "pl", "Krakow Airport", "Europe/Warsaw"),
  location("waw-airport", "pl", "Warsaw Airport", "Europe/Warsaw"),
];
const carTypes = [
  { code: "SEDAN", name: "Sedan", active: true },
  { code: "SUV", name: "SUV", active: true },
];
const carClasses = [{ code: "ECONOMY", name: "Economy", active: true }];
const result = {
  pickUpLocation: {
    id: "waw-airport",
    country: "pl",
    name: "Warsaw Airport",
    timeZone: "Europe/Warsaw",
    active: true,
  },
  returnLocation: {
    id: "krk-airport",
    country: "pl",
    name: "Krakow Airport",
    timeZone: "Europe/Warsaw",
    active: true,
  },
  start: "2026-12-24T08:00:00Z",
  days: 3,
  end: "2026-12-27T08:00:00Z",
  offers: [
    {
      carType: "SEDAN",
      carTypeName: "Sedan",
      carClass: "ECONOMY",
      carClassName: "Economy",
      availableCars: 5,
      dailyRate: { amount: "149.00", currency: "PLN" },
    },
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
const referenceRoutes = [
  get("/api/v1/countries", countries),
  get("/api/v1/locations", locations),
  get("/api/v1/car-types", carTypes),
  get("/api/v1/car-classes", carClasses),
];
const insuranceRoute = get("/api/v1/insurance-options", [
  {
    code: "BASIC",
    name: "Basic cover",
    description: "Liability and damage above an excess",
    preselected: true,
    pricePerDay: { amount: "10.00", currency: "PLN" },
  },
  {
    code: "FULL",
    name: "Full cover",
    description: "Without excess",
    preselected: false,
    pricePerDay: { amount: "50.00", currency: "PLN" },
  },
]);
const searchUrl =
  "/?country=pl&pickUp=waw-airport&returnTo=krk-airport&start=2026-12-24T08%3A00%3A00.000Z&days=3";

beforeEach(() => screenWidth(1280));
afterEach(() => vi.unstubAllGlobals());

const searchCall = (calls: Call[]) => calls.find((c) => c.path === "/api/v1/search");

describe("Search", () => {
  it("shows the offers of the search in the address bar, in local time", async () => {
    const calls = fakeApi([...referenceRoutes, get("/api/v1/search", result)]);
    renderPage(<App />, searchUrl);

    expect(await screen.findByRole("heading", { name: "Sedan" })).toBeInTheDocument();
    expect(screen.getByText("5 cars available")).toBeInTheDocument();
    expect(screen.getByText("1 car available")).toBeInTheDocument();
    expect(screen.getAllByText("PLN 149.00")).toHaveLength(2);
    expect(screen.getByText("Warsaw Airport → Krakow Airport")).toBeInTheDocument();
    expect(screen.getByText(/2026-12-24 09:00 – 2026-12-27 09:00/)).toBeInTheDocument();
    const call = searchCall(calls);
    expect(call?.headers.get("X-Country")).toBe("pl");
    expect(new URLSearchParams(call?.search).get("returnLocationId")).toBe("krk-airport");
    expect(new URLSearchParams(call?.search).get("days")).toBe("3");
  });

  it("explains when no car is free", async () => {
    fakeApi([...referenceRoutes, get("/api/v1/search", { ...result, offers: [] })]);
    renderPage(<App />, searchUrl);

    expect(await screen.findByText(/No cars are free for this search/)).toBeInTheDocument();
  });

  it("shows the explanation of the service when a search is refused", async () => {
    fakeApi([
      ...referenceRoutes,
      {
        method: "GET",
        path: "/api/v1/search",
        answer: () => ({
          status: 400,
          body: { type: "/problems/invalid-search", detail: "The start must be in the future" },
        }),
      },
    ]);
    renderPage(<App />, searchUrl);

    expect(await screen.findByText("The start must be in the future")).toBeInTheDocument();
  });

  it("asks for a pick-up location before searching", async () => {
    const calls = fakeApi(referenceRoutes);
    const user = userEvent.setup();
    renderPage(<App />, "/");

    await user.click(await screen.findByRole("button", { name: "Search" }));

    expect(await screen.findByText("Choose where you pick up the car.")).toBeInTheDocument();
    expect(searchCall(calls)).toBeUndefined();
  });

  it("offers return locations only in the country of the pick-up location", async () => {
    fakeApi(referenceRoutes);
    const user = userEvent.setup();
    renderPage(<App />, "/");

    await user.click(await screen.findByRole("combobox", { name: /Pick-up location/ }));
    await user.click(await screen.findByRole("option", { name: "Warsaw Airport" }));
    await user.click(screen.getByRole("combobox", { name: /Return location/ }));
    const options = within(await screen.findByRole("listbox")).getAllByRole("option");

    expect(options.map((o) => o.textContent)).toEqual(["Same as pick-up", "Krakow Airport"]);
  });

  it("names every field of the form", async () => {
    fakeApi(referenceRoutes);
    renderPage(<App />, "/");

    for (const name of [/Pick-up location/, /Return location/, /Car type/, /Car class/]) {
      expect(await screen.findByRole("combobox", { name })).toBeInTheDocument();
    }
    expect(screen.getByRole("spinbutton", { name: /Days/ })).toBeInTheDocument();
  });
});

const extrasRoute = get("/api/v1/extras", [
  {
    code: "CHILD_SEAT",
    name: "Child seat",
    kind: "EQUIPMENT",
    charge: "PER_DAY",
    price: { amount: "25.00", currency: "PLN" },
    maxQuantity: 3,
    active: true,
  },
  {
    code: "ROAD_TOLL",
    name: "Road toll payment",
    kind: "SERVICE",
    charge: "PER_RENTAL",
    price: { amount: "49.00", currency: "PLN" },
    maxQuantity: 1,
    active: true,
  },
]);
const feeRoute = get("/api/v1/one-way-fee", { amount: "150.00", currency: "PLN" });

describe("Selection", () => {
  it("opens the chosen car with its insurance, extras and one-way fee at list prices", async () => {
    fakeApi([
      ...referenceRoutes,
      get("/api/v1/search", result),
      insuranceRoute,
      extrasRoute,
      feeRoute,
    ]);
    const user = userEvent.setup();
    renderPage(<App />, searchUrl);

    await user.click(await screen.findByRole("button", { name: "Choose SUV Economy" }));

    expect(await screen.findByRole("heading", { name: "SUV · Economy" })).toBeInTheDocument();
    expect(screen.getByText("Payment: at pick-up.")).toBeInTheDocument();
    expect(screen.getByText("Car rental: PLN 149.00 per day, list price.")).toBeInTheDocument();
    const insurance = await screen.findByRole("group", { name: "Insurance" });
    expect(
      within(insurance).getByRole("radio", { name: /^Basic cover: from PLN\s10\.00 per day$/ }),
    ).toBeChecked();
    expect(
      within(insurance).getByRole("radio", { name: /^Full cover: from PLN\s50\.00 per day$/ }),
    ).not.toBeChecked();
    const extras = await screen.findByRole("region", { name: "Extras" });
    expect(within(extras).getByText("Child seat: PLN 25.00 per day")).toBeInTheDocument();
    expect(within(extras).getByText("Road toll payment: PLN 49.00 per rental")).toBeInTheDocument();
    expect(
      await screen.findByText(
        "One-way fee: PLN 150.00, because the car is returned to another location.",
      ),
    ).toBeInTheDocument();
    expect(
      await screen.findByRole("button", { name: "Sign in to see the price and reserve" }),
    ).toBeEnabled();
    await user.click(screen.getByRole("link", { name: "Back to the results" }));
    expect(await screen.findByRole("heading", { name: "Sedan" })).toBeInTheDocument();
  });

  it("keeps the chosen insurance and extras in the address for the sign-in", async () => {
    fakeApi([
      ...referenceRoutes,
      get("/api/v1/search", result),
      insuranceRoute,
      extrasRoute,
      feeRoute,
    ]);
    const go = vi.fn<(url: string) => void>();
    const session = await import("../session");
    vi.spyOn(session.browser, "go").mockImplementation(go);
    const user = userEvent.setup();
    renderPage(<App />, `/selection${searchUrl.slice(1)}&carType=SUV&carClass=ECONOMY`);

    await user.click(await screen.findByRole("radio", { name: /Full cover/ }));
    await user.click(await screen.findByRole("combobox", { name: "How many: Child seat" }));
    await user.click(await screen.findByRole("option", { name: "2" }));
    await user.click(screen.getByRole("button", { name: "Sign in to see the price and reserve" }));

    const kept = sessionStorage.getItem("ui:after-sign-in") ?? "";
    expect(kept).toContain("insurance=FULL");
    expect(kept).toContain("extras=CHILD_SEAT%3A2");
    expect(go).toHaveBeenCalled();
    vi.restoreAllMocks();
  });

  it("says so when insurance prices are not available", async () => {
    fakeApi([
      ...referenceRoutes,
      get("/api/v1/search", result),
      extrasRoute,
      feeRoute,
      {
        method: "GET",
        path: "/api/v1/insurance-options",
        answer: () => ({ status: 503, body: { title: "Insurance prices unavailable" } }),
      },
    ]);
    renderPage(<App />, `/selection${searchUrl.slice(1)}&carType=SUV&carClass=ECONOMY`);

    expect(
      await screen.findByText("Insurance prices are not available right now."),
    ).toBeInTheDocument();
    expect(screen.getByText("Payment: at pick-up.")).toBeInTheDocument();
  });

  it("warns when the chosen car was taken in the meantime", async () => {
    fakeApi([...referenceRoutes, get("/api/v1/search", { ...result, offers: [] })]);
    renderPage(<App />, `/selection${searchUrl.slice(1)}&carType=SUV&carClass=ECONOMY`);

    expect(await screen.findByText(/no longer free for your dates/)).toBeInTheDocument();
  });

  it("asks to choose a car first when the address is incomplete", async () => {
    fakeApi(referenceRoutes);
    renderPage(<App />, "/selection");

    expect(
      await screen.findByText("Choose a car from the search results first."),
    ).toBeInTheDocument();
  });
});

describe("The pick-up date and time", () => {
  // Noon UTC is 13:00 in Warsaw on this day.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-11-12T12:00:00Z"));
  });
  afterEach(() => vi.useRealTimers());

  const chooseWarsaw = async (user: ReturnType<typeof userEvent.setup>) => {
    await user.click(await screen.findByRole("combobox", { name: /Pick-up location/ }));
    await user.click(await screen.findByRole("option", { name: "Warsaw Airport" }));
  };

  it("shows the pick-up of the address bar in the local time of the location", async () => {
    fakeApi([...referenceRoutes, get("/api/v1/search", result)]);
    renderPage(<App />, searchUrl);

    expect(
      await screen.findByRole("button", {
        name: "Pick-up date and time: Thu, 24 Dec 2026 · 09:00",
      }),
    ).toBeInTheDocument();
  });

  it("searches at the day and time chosen on the clocks of the location", async () => {
    const calls = fakeApi([...referenceRoutes, get("/api/v1/search", result)]);
    const user = userEvent.setup();
    renderPage(<App />, "/");
    await chooseWarsaw(user);

    await user.click(screen.getByRole("button", { name: /^Pick-up date and time/ }));
    const picker = await screen.findByRole("dialog");
    await user.click(within(picker).getByRole("button", { name: "Next month" }));
    await user.click(await within(picker).findByRole("gridcell", { name: "15" }));
    await user.click(within(picker).getByRole("button", { name: "14:30" }));
    expect(within(picker).getByText("Fri, 18 Dec, 14:30")).toBeInTheDocument();
    await user.click(within(picker).getByRole("button", { name: "Done" }));
    await user.click(screen.getByRole("button", { name: "Search" }));

    // Warsaw is an hour ahead of UTC in December.
    await waitFor(() => expect(searchCall(calls)).toBeDefined());
    expect(decodeURIComponent(searchCall(calls)!.search)).toContain(
      "start=2026-12-15T13:30:00.000Z",
    );
  });

  it("offers only later times today, and moves the time to the first one", async () => {
    fakeApi(referenceRoutes);
    const user = userEvent.setup();
    renderPage(<App />, "/");
    await chooseWarsaw(user);

    await user.click(screen.getByRole("button", { name: /^Pick-up date and time/ }));
    const picker = await screen.findByRole("dialog");
    await user.click(within(picker).getByRole("gridcell", { name: "12" }));

    expect(within(picker).getByRole("button", { name: "13:00" })).toBeDisabled();
    expect(within(picker).getByRole("button", { name: "13:30" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
    expect(within(picker).getByRole("gridcell", { name: "11" })).toBeDisabled();
  });

  it("changes the days with the buttons beside them", async () => {
    fakeApi(referenceRoutes);
    const user = userEvent.setup();
    renderPage(<App />, "/");

    await user.click(await screen.findByRole("button", { name: "One day more" }));
    expect(screen.getByRole("spinbutton", { name: /Days/ })).toHaveValue(4);
    await user.click(screen.getByRole("button", { name: "One day less" }));
    await user.click(screen.getByRole("button", { name: "One day less" }));
    await user.click(screen.getByRole("button", { name: "One day less" }));
    expect(screen.getByRole("spinbutton", { name: /Days/ })).toHaveValue(1);
    expect(screen.getByRole("button", { name: "One day less" })).toBeDisabled();
  });
});

describe("On a phone", () => {
  it("shows the form and every offer", async () => {
    screenWidth(375);
    fakeApi([...referenceRoutes, get("/api/v1/search", result)]);
    renderPage(<App />, searchUrl);

    await waitFor(() =>
      expect(screen.getAllByRole("button", { name: /^Choose .+ Economy$/ })).toHaveLength(2),
    );
    expect(screen.getByRole("button", { name: "Search" })).toBeInTheDocument();
  });
});
