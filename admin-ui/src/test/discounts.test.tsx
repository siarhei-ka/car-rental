import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { App } from "../App";
import { fakeApi, get, renderPage, screenWidth } from "./harness";

const references = [
  get("/api/v1/admin/countries", [{ code: "pl", name: "Poland", currency: "PLN", active: true }]),
  get("/api/v1/admin/extras", [
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
      code: "GPS",
      name: "Satellite navigation",
      kind: "EQUIPMENT",
      charge: "PER_DAY",
      price: { amount: "20.00", currency: "PLN" },
      maxQuantity: 1,
      active: true,
    },
  ]),
];

const threeExtras = {
  id: "3f1c6a52-1111-4a5e-9a2b-000000000001",
  name: "3 or more extras: 10% off the extras",
  conditions: { minExtras: 3, requiredExtras: [], minDays: null },
  effect: { kind: "PERCENT_OFF", percent: "10", amount: null, target: "EXTRAS" },
  validFrom: "2026-01-01",
  validTo: null,
  active: true,
};
const seatAndGps = {
  id: "3f1c6a52-1111-4a5e-9a2b-000000000002",
  name: "Seat and navigation",
  conditions: { minExtras: null, requiredExtras: ["GPS", "CHILD_SEAT"], minDays: 7 },
  effect: {
    kind: "AMOUNT_OFF",
    percent: null,
    amount: { amount: "20.00", currency: "PLN" },
    target: "TOTAL",
  },
  validFrom: "2026-12-01",
  validTo: "2026-12-31",
  active: false,
};

beforeEach(() => {
  screenWidth(1280);
  localStorage.setItem("admin-ui.country", "pl");
});
afterEach(() => vi.unstubAllGlobals());

describe("Discount rules", () => {
  it("describes each rule", async () => {
    fakeApi([...references, get("/api/v1/admin/discount-rules", [threeExtras, seatAndGps])]);
    renderPage(<App />, "/discounts");

    const first = (await screen.findByRole("cell", { name: threeExtras.name })).closest("tr")!;
    expect(within(first).getByText("3 or more extras")).toBeInTheDocument();
    expect(within(first).getByText("10% off the extras")).toBeInTheDocument();
    expect(within(first).getByText("From 2026-01-01")).toBeInTheDocument();
    expect(within(first).getByText("Active")).toBeInTheDocument();

    const second = screen.getByRole("cell", { name: "Seat and navigation" }).closest("tr")!;
    expect(
      within(second).getByText("Child seat and Satellite navigation, 7 or more days"),
    ).toBeInTheDocument();
    expect(within(second).getByText("PLN 20.00 off the total")).toBeInTheDocument();
    expect(within(second).getByText("2026-12-01 to 2026-12-31")).toBeInTheDocument();
    expect(within(second).getByText("Inactive")).toBeInTheDocument();
  });

  it("adds a rule in the administrator's country", async () => {
    const calls = fakeApi([
      ...references,
      get("/api/v1/admin/discount-rules", []),
      {
        method: "POST",
        path: "/api/v1/admin/discount-rules",
        answer: () => ({ status: 201, body: threeExtras }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/discounts");

    await user.click(await screen.findByRole("button", { name: "Add rule" }));
    const dialog = await screen.findByRole("dialog");
    await user.type(within(dialog).getByLabelText(/Name/), "Week");
    await user.type(within(dialog).getByLabelText(/At least this many days/), "7");
    await user.type(within(dialog).getByLabelText(/Percentage/), "5,5");
    await user.clear(within(dialog).getByLabelText(/First day/));
    await user.type(within(dialog).getByLabelText(/First day/), "2026-12-01");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    const post = calls.find((c) => c.method === "POST");
    expect(post?.headers.get("X-Country")).toBe("pl");
    expect(post?.body).toEqual({
      name: "Week",
      conditions: { minExtras: null, requiredExtras: [], minDays: 7 },
      effect: { kind: "PERCENT_OFF", percent: "5.5", amount: null, target: "EXTRAS" },
      validFrom: "2026-12-01",
      validTo: null,
      active: true,
    });
  });

  it("changes a rule and shows a refusal", async () => {
    const calls = fakeApi([
      ...references,
      get("/api/v1/admin/discount-rules", [seatAndGps]),
      {
        method: "PUT",
        path: `/api/v1/admin/discount-rules/${seatAndGps.id}`,
        answer: () => ({ status: 400, body: { detail: "An amount off is more than 0" } }),
      },
    ]);
    const user = userEvent.setup();
    renderPage(<App />, "/discounts");

    await user.click(await screen.findByRole("button", { name: "Edit Seat and navigation" }));
    const dialog = await screen.findByRole("dialog");
    const amount = within(dialog).getByLabelText(/Amount/);
    expect(amount).toHaveValue("20.00");
    await user.clear(amount);
    await user.type(amount, "0");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    expect(await within(dialog).findByText("An amount off is more than 0")).toBeInTheDocument();
    const put = calls.find((c) => c.method === "PUT");
    expect(put?.body).toMatchObject({
      conditions: { minExtras: null, requiredExtras: ["CHILD_SEAT", "GPS"], minDays: 7 },
      effect: { kind: "AMOUNT_OFF", percent: null, amount: "0", target: "TOTAL" },
      validTo: "2026-12-31",
      active: false,
    });
  });
});
