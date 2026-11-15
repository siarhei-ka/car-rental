// The load check of NFR-5 and NFR-6: 20 requests per second from up to 50 users, through the
// customer UI's address, with 95% of the answers within 300 ms. Run it against a started system:
//
//   docker run --rm -i --add-host=host.docker.internal:host-gateway --env-file .env \
//     grafana/k6:1.5.0 run - < load/load-test.js
//
// Most requests are what a visitor does: reference data, searches and list prices. A share are
// final prices of the test customer, who signs in once at the start.
import http from "k6/http";
import { check, fail } from "k6";

const BASE = __ENV.BASE_URL || "http://host.docker.internal:8080";

export const options = {
  scenarios: {
    visitors: {
      executor: "constant-arrival-rate",
      rate: Number(__ENV.RATE || 20),
      timeUnit: "1s",
      duration: __ENV.DURATION || "2m",
      preAllocatedVUs: 50,
      maxVUs: 50,
    },
  },
  thresholds: {
    // NFR-6: 95% of the answers within 300 ms, for all requests and for each kind.
    http_req_duration: ["p(95)<300"],
    "http_req_duration{name:search}": ["p(95)<300"],
    "http_req_duration{name:quote}": ["p(95)<300"],
    "http_req_duration{name:locations}": ["p(95)<300"],
    "http_req_duration{name:car-types}": ["p(95)<300"],
    "http_req_duration{name:rates}": ["p(95)<300"],
    "http_req_duration{name:extras}": ["p(95)<300"],
    "http_req_duration{name:insurance}": ["p(95)<300"],
    http_req_failed: ["rate<0.01"],
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "max"],
  // The sign-in at the start is not part of the measured load.
  setupTimeout: "60s",
};

// The browser's view of the system names localhost; from inside Docker it is the host. Each request
// still names localhost in its Host header, so that the addresses the gateway and Keycloak build,
// such as the address to return to after the sign-in, are the ones the realm allows.
function inside(url) {
  return url.replace("http://localhost:", BASE.replace(/:\d+$/, ":"));
}

function hostOf(url) {
  return "localhost:" + url.match(/:(\d+)(\/|$)/)[1];
}

function get(url, params = {}) {
  return http.get(url, { ...params, headers: { ...params.headers, Host: hostOf(url) } });
}

function post(url, body, params = {}) {
  return http.post(url, body, { ...params, headers: { ...params.headers, Host: hostOf(url) } });
}

function put(url, body, params = {}) {
  return http.put(url, body, { ...params, headers: { ...params.headers, Host: hostOf(url) } });
}

function cookieHeader(response, existing) {
  const cookies = { ...existing };
  for (const [name, list] of Object.entries(response.cookies)) {
    if (list.length > 0) cookies[name] = list[0].value;
  }
  return cookies;
}

function asHeader(cookies) {
  return Object.entries(cookies)
    .map(([name, value]) => `${name}=${value}`)
    .join("; ");
}

/** Signs the test customer in through the gateway and Keycloak, following each redirect by hand. */
export function setup() {
  const password = __ENV.KEYCLOAK_TEST_USER_PASSWORD;
  if (!password) fail("Set KEYCLOAK_TEST_USER_PASSWORD, for example with --env-file .env");
  let app = {};
  let keycloak = {};
  let response = get(`${BASE}/oauth2/authorization/keycloak`, { redirects: 0 });
  app = cookieHeader(response, app);
  response = get(inside(response.headers.Location), { redirects: 0 });
  keycloak = cookieHeader(response, keycloak);
  const action = response.html().find("form#kc-form-login").attr("action").replace(/&amp;/g, "&");
  response = post(
    inside(action),
    { username: "customer", password, credentialId: "" },
    { redirects: 0, headers: { Cookie: asHeader(keycloak) } },
  );
  response = get(inside(response.headers.Location), {
    redirects: 0,
    headers: { Cookie: asHeader(app) },
  });
  app = cookieHeader(response, app);
  response = get(`${BASE}/api/v1/session`, { headers: { Cookie: asHeader(app) } });
  app = cookieHeader(response, app);
  if (!response.json("signedIn")) fail("The test customer could not sign in");
  const headers = {
    Cookie: asHeader(app),
    "X-XSRF-TOKEN": decodeURIComponent(app["XSRF-TOKEN"] || ""),
    "Content-Type": "application/json",
  };
  put(
    `${BASE}/api/v1/profile`,
    JSON.stringify({ dateOfBirth: "1990-05-17", licenceCountry: "pl" }),
    { headers },
  );
  return { headers };
}

const SEARCHES = [
  ["pl", "waw-airport", "krk-airport"],
  ["pl", "krk-airport", "krk-airport"],
  ["pl", "waw-center", "waw-airport"],
  ["de", "ber-airport", "ber-airport"],
  ["de", "muc-center", "ber-airport"],
];

function start() {
  const day = new Date(Date.now() + (20 + Math.floor(Math.random() * 200)) * 86_400_000);
  day.setUTCHours(8 + Math.floor(Math.random() * 9), 0, 0, 0);
  return day.toISOString();
}

export default function (signedIn) {
  const [country, pickUp, returnTo] = SEARCHES[Math.floor(Math.random() * SEARCHES.length)];
  const local = { headers: { "X-Country": country } };
  const roll = Math.random();
  let response;
  if (roll < 0.4) {
    response = get(
      `${BASE}/api/v1/search?pickUpLocationId=${pickUp}&returnLocationId=${returnTo}` +
        `&start=${start()}&days=${1 + Math.floor(Math.random() * 7)}`,
      { ...local, tags: { name: "search" } },
    );
  } else if (roll < 0.55) {
    response = get(`${BASE}/api/v1/locations`, { tags: { name: "locations" } });
  } else if (roll < 0.65) {
    response = get(`${BASE}/api/v1/car-types`, { tags: { name: "car-types" } });
  } else if (roll < 0.73) {
    response = get(`${BASE}/api/v1/rates?locationId=${pickUp}`, {
      ...local,
      tags: { name: "rates" },
    });
  } else if (roll < 0.79) {
    response = get(`${BASE}/api/v1/extras`, { ...local, tags: { name: "extras" } });
  } else if (roll < 0.85) {
    response = get(`${BASE}/api/v1/insurance-options`, {
      ...local,
      tags: { name: "insurance" },
    });
  } else {
    response = post(
      `${BASE}/api/v1/quotes`,
      JSON.stringify({
        pickUpLocationId: "waw-airport",
        returnLocationId: Math.random() < 0.5 ? "waw-airport" : "krk-airport",
        start: start(),
        days: 1 + Math.floor(Math.random() * 7),
        carType: "SEDAN",
        carClass: "ECONOMY",
        insurance: Math.random() < 0.5 ? "BASIC" : "FULL",
        extras: Math.random() < 0.5 ? [{ code: "CHILD_SEAT", quantity: 1 }] : [],
      }),
      { headers: { ...signedIn.headers, "X-Country": "pl" }, tags: { name: "quote" } },
    );
  }
  check(response, { "answered with 200": (r) => r.status === 200 });
}
