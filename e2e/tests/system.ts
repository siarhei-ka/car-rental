import { readFileSync } from "node:fs";
import { expect, type Page } from "@playwright/test";

/** Where the two UIs run. Docker Compose publishes them on these ports. */
export const CUSTOMER_UI = process.env.CUSTOMER_UI_URL ?? "http://localhost:8080";
export const ADMIN_UI = process.env.ADMIN_UI_URL ?? "http://localhost:8090";

/**
 * The password of the realm's test users. It comes from the environment, or from the `.env` file
 * the system was started with; it is never written to the test output.
 */
function testPassword(): string {
  const fromEnvironment = process.env.KEYCLOAK_TEST_USER_PASSWORD;
  if (fromEnvironment) {
    return fromEnvironment;
  }
  const line = readFileSync(new URL("../../.env", import.meta.url), "utf8")
    .split("\n")
    .find((l) => l.startsWith("KEYCLOAK_TEST_USER_PASSWORD="));
  if (!line) {
    throw new Error("Set KEYCLOAK_TEST_USER_PASSWORD or start the system from .env");
  }
  return line.slice(line.indexOf("=") + 1).trim();
}

/** Signs in on the Keycloak page the UI has sent the browser to. */
export async function signInOnKeycloak(page: Page, username: string) {
  await expect(page.locator("#kc-login")).toBeVisible();
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(testPassword());
  await page.locator("#kc-login").click();
}

/**
 * A pick-up time no other test run uses: a random day 60 to 360 days ahead, at a random full hour
 * from 8 to 17, local time in Warsaw. Each run reserves its own car this way.
 */
export function uniqueStart(): { day: Date; hour: number } {
  const day = new Date();
  day.setDate(day.getDate() + 60 + Math.floor(Math.random() * 300));
  return { day, hour: 8 + Math.floor(Math.random() * 10) };
}

/** The page has no horizontal scrolling, as on a phone of 360 px or more (NFR-33). */
export async function expectNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
  );
  expect(overflow).toBeLessThanOrEqual(0);
}
