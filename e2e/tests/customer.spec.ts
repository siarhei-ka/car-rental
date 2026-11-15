import { expect, test } from "@playwright/test";
import { reserveACar } from "./flows";
import { CUSTOMER_UI, expectNoHorizontalScroll } from "./system";

test.describe("Customer", () => {
  test("finds a car, reserves it at the price shown, and cancels it", async ({ page }) => {
    const reserved = await reserveACar(page, true);

    await page.getByRole("link", { name: "All your reservations" }).click();
    await expect(page.getByRole("heading", { name: "Your reservations" })).toBeVisible();
    await expectNoHorizontalScroll(page);
    await page.goto(`${CUSTOMER_UI}/reservations/${reserved.country}/${reserved.id}`);
    await expect(page.getByText("Confirmed", { exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Cancel this reservation" }).click();
    await expect(page.getByText("Cancelled", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Cancel this reservation" })).toHaveCount(0);
  });

  test("a visitor searches without signing in and is asked to sign in for the price", async ({
    page,
  }) => {
    await page.goto(
      `${CUSTOMER_UI}/selection?country=de&pickUp=ber-airport&returnTo=ber-airport` +
        "&start=2027-06-01T08%3A00%3A00.000Z&days=3&carType=SEDAN&carClass=ECONOMY",
    );
    await expect(page.getByRole("heading", { name: "Sedan · Economy" })).toBeVisible();
    await expect(
      page.getByRole("radio", { name: /^Basic cover: from €10\.00 per day$/ }),
    ).toBeChecked();
    await expect(
      page.getByRole("button", { name: "Sign in to see the price and reserve" }),
    ).toBeVisible();
    await expectNoHorizontalScroll(page);
  });

  test("on a phone, picks the pick-up date and time by touch", async ({ page, isMobile }) => {
    test.skip(!isMobile, "The touch picker is the phone's");
    await page.goto(CUSTOMER_UI);
    await page.getByRole("combobox", { name: "Pick-up location" }).tap();
    await page.getByRole("option", { name: "Warsaw Chopin Airport" }).tap();

    await page.getByRole("button", { name: /^Pick-up date and time/ }).tap();
    const picker = page.getByRole("dialog");
    await picker.getByRole("button", { name: "Next month" }).tap();
    // While the month slides in, the old and the new month are both shown.
    await expect(picker.getByRole("gridcell", { name: "15" })).toHaveCount(1);
    await picker.getByRole("gridcell", { name: "15" }).tap();
    await picker.getByRole("button", { name: "10:00", exact: true }).tap();
    await expect(picker.getByText(/^\w{3}, \d{1,2} \w{3}, 10:00$/)).toBeVisible();
    await picker.getByRole("button", { name: "Done" }).tap();

    await page.getByRole("button", { name: "Search", exact: true }).tap();
    // 10:00 in Warsaw is 09:00 or 08:00 UTC, depending on daylight saving time.
    await expect(page).toHaveURL(/start=\d{4}-\d{2}-15T0[89]%3A00%3A00\.000Z/);
    await expect(page.getByRole("button", { name: /^Choose / }).first()).toBeVisible();
    await expectNoHorizontalScroll(page);
  });
});
