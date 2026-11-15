import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests of the whole system, started with Docker Compose. Every test runs on a desktop
 * browser and on a phone (NFR-37). The tests share the test users of the realm, so they run one at
 * a time.
 */
export default defineConfig({
  testDir: "tests",
  timeout: 90_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  forbidOnly: Boolean(process.env.CI),
  reporter: process.env.CI ? [["list"], ["html", { open: "never" }]] : [["list"]],
  use: {
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    locale: "en-GB",
    timezoneId: "Europe/Warsaw",
    // Over the local HTTPS proxy, the browsers of the tests do not know its local authority.
    ignoreHTTPSErrors: Boolean(process.env.E2E_LOCAL_HTTPS),
  },
  projects: [
    { name: "desktop", use: { ...devices["Desktop Chrome"] } },
    { name: "phone", use: { ...devices["Pixel 7"] } },
    // The other browsers of NFR-32, run on request with E2E_ALL_BROWSERS=1.
    ...(process.env.E2E_ALL_BROWSERS
      ? [
          { name: "desktop-firefox", use: { ...devices["Desktop Firefox"] } },
          { name: "desktop-safari", use: { ...devices["Desktop Safari"] } },
          { name: "phone-safari", use: { ...devices["iPhone 15"] } },
        ]
      : []),
  ],
});
