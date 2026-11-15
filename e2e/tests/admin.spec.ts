import { expect, test } from "@playwright/test";
import { reserveACar } from "./flows";
import { ADMIN_UI, expectNoHorizontalScroll, signInOnKeycloak } from "./system";

test.describe("Administrator", () => {
  test("registers the pick-up and the return of a customer's reservation", async ({
    page,
    browser,
  }) => {
    const reserved = await reserveACar(page, false);

    // The administrator works in a browser of their own.
    const desk = await browser.newContext({
      viewport: page.viewportSize() ?? undefined,
      locale: "en-GB",
      timezoneId: "Europe/Warsaw",
    });
    const admin = await desk.newPage();
    await admin.goto(ADMIN_UI);
    await admin.getByRole("button", { name: "Sign in" }).click();
    await signInOnKeycloak(admin, "admin-pl");

    await admin.goto(`${ADMIN_UI}/reservations`);
    await expect(admin.getByRole("heading", { name: "Reservations" })).toBeVisible();
    await admin.getByRole("combobox", { name: "Pick-up location" }).click();
    await admin.getByRole("option", { name: "Warsaw Chopin Airport" }).click();

    await admin.getByRole("button", { name: `Pick up: reservation ${reserved.id}` }).click();
    await admin.getByRole("button", { name: `Return: reservation ${reserved.id}` }).click();
    await expect(
      admin.getByRole("button", { name: `Return: reservation ${reserved.id}` }),
    ).toHaveCount(0);
    await expectNoHorizontalScroll(admin);
    await desk.close();

    await page.reload();
    await expect(page.getByText("Returned", { exact: true })).toBeVisible();
  });

  test("a country administrator sees the discount rules of the country", async ({ page }) => {
    await page.goto(ADMIN_UI);
    await page.getByRole("button", { name: "Sign in" }).click();
    await signInOnKeycloak(page, "admin-pl");
    await page.goto(`${ADMIN_UI}/discounts`);
    await expect(page.getByText("3 or more extras: 10% off the extras").first()).toBeVisible();
    await expectNoHorizontalScroll(page);
  });
});
