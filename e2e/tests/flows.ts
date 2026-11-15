import { expect, type Page } from "@playwright/test";
import { CUSTOMER_UI, expectNoHorizontalScroll, signInOnKeycloak, uniqueStart } from "./system";

/** A reservation the customer made, as its page shows it. */
export type Reserved = { id: string; country: string; start: string };

const pad = (n: number) => String(n).padStart(2, "0");

/** Chooses the pick-up day and hour in the picker of the search form. */
export async function pickUpAt(page: Page, day: Date, hour: number) {
  await page.getByRole("button", { name: /^Pick-up date and time/ }).click();
  const picker = page.getByRole("dialog");
  const month = day.toLocaleString("en-GB", { month: "long", year: "numeric" });
  for (let i = 0; i < 13 && !(await picker.getByText(month, { exact: true }).isVisible()); i++) {
    await picker.getByRole("button", { name: "Next month" }).click();
    // The next month slides in; it is wanted once the old one has gone.
    await expect(picker.getByRole("gridcell", { name: "1", exact: true })).toHaveCount(1);
  }
  await picker.getByRole("gridcell", { name: String(day.getDate()), exact: true }).click();
  await picker.getByRole("button", { name: `${pad(hour)}:00`, exact: true }).click();
  await picker.getByRole("button", { name: "Done" }).click();
  await expect(picker).toHaveCount(0);
}

/**
 * The whole customer flow up to a reservation: search from Warsaw Chopin Airport, choose a Sedan
 * Economy with full cover and two child seats, sign in as the test customer, enter the driver
 * profile if asked, see the price and reserve it.
 *
 * @param oneWay return the car to Krakow Airport. A one-way car that is returned stays in Krakow,
 *     so a test that returns the car keeps it in Warsaw, and every run finds a free car
 */
export async function reserveACar(page: Page, oneWay: boolean): Promise<Reserved> {
  const { day, hour } = uniqueStart();
  await page.goto(CUSTOMER_UI);
  await expect(page.getByRole("heading", { name: "Find a car" })).toBeVisible();

  await page.getByRole("combobox", { name: "Pick-up location" }).click();
  await page.getByRole("option", { name: "Warsaw Chopin Airport" }).click();
  if (oneWay) {
    await page.getByRole("combobox", { name: "Return location" }).click();
    await page.getByRole("option", { name: "Krakow Airport" }).click();
  }
  await pickUpAt(page, day, hour);
  await page.getByRole("spinbutton", { name: "Days" }).fill("2");
  await page.getByRole("button", { name: "Search", exact: true }).click();

  await expect(page.getByRole("button", { name: "Choose Sedan Economy" })).toBeVisible();
  await expectNoHorizontalScroll(page);
  await page.getByRole("button", { name: "Choose Sedan Economy" }).click();

  await expect(page.getByRole("heading", { name: "Sedan · Economy" })).toBeVisible();
  // The choice goes through the address bar, so the radio button changes a moment later.
  await page.getByRole("radio", { name: /^Full cover/ }).click();
  await expect(page.getByRole("radio", { name: /^Full cover/ })).toBeChecked();
  await page.getByRole("combobox", { name: "How many: Child seat" }).click();
  await page.getByRole("option", { name: "2", exact: true }).click();
  if (oneWay) {
    await expect(page.getByText(/^One-way fee: /)).toBeVisible();
  }
  await expectNoHorizontalScroll(page);

  const signIn = page.getByRole("button", { name: "Sign in to see the price and reserve" });
  if (await signIn.isVisible()) {
    await signIn.click();
    await signInOnKeycloak(page, "customer");
  }
  await enterProfileIfAsked(page);

  await page.getByRole("link", { name: "See the price" }).click();
  await expect(page.getByRole("heading", { name: "Your price" })).toBeVisible();
  const price = page.getByLabel("Price");
  await expect(price.getByText(/^Full cover, /)).toBeVisible();
  await expect(price.getByText(/^Child seat × 2, /)).toBeVisible();
  if (oneWay) {
    await expect(price.getByText("One-way fee", { exact: true })).toBeVisible();
  }
  await expect(price.getByText("Total, due at pick-up")).toBeVisible();
  await expectNoHorizontalScroll(page);

  await page.getByRole("button", { name: "Reserve, pay at pick-up" }).click();
  await expect(page.getByRole("heading", { name: "Your car is reserved" })).toBeVisible();
  await expect(page.getByText(/^Pay .+ when you pick up the car\.$/)).toBeVisible();
  const [, country, id] = new URL(page.url()).pathname.split("/").filter(Boolean);
  await expect(page.getByText(`Reservation ${id}`)).toBeVisible();
  return {
    id: id ?? "",
    country: country ?? "",
    start: `${day.getFullYear()}-${pad(day.getMonth() + 1)}-${pad(day.getDate())} ${pad(hour)}:00`,
  };
}

/** On the first sign-in the customer enters the driver profile, then comes back. */
async function enterProfileIfAsked(page: Page) {
  const profile = page.getByRole("heading", { name: "Driver profile" });
  const seePrice = page.getByRole("link", { name: "See the price" });
  await expect(profile.or(seePrice)).toBeVisible();
  if (await profile.isVisible()) {
    await page.getByRole("spinbutton", { name: "Year" }).fill("1990");
    await page.getByRole("spinbutton", { name: "Month" }).fill("05");
    await page.getByRole("spinbutton", { name: "Day" }).fill("17");
    await page
      .getByRole("combobox", { name: /Country that issued your driving licence/ })
      .fill("Pola");
    await page.getByRole("option", { name: "Poland" }).click();
    await page.getByRole("button", { name: "Save" }).click();
    await expect(seePrice).toBeVisible();
  }
}
