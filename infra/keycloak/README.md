# Keycloak realm

`car-rental-realm.json` is imported when Keycloak starts and the realm does not exist yet. Later
changes to the file need a fresh Keycloak database, for example `docker compose down -v`.

The file takes these values from the environment of the Keycloak container:

| Variable | Use |
|---|---|
| `ADMIN_GATEWAY_CLIENT_SECRET` | Secret of the client `admin-ui`, used by the admin gateway |
| `CUSTOMER_GATEWAY_CLIENT_SECRET` | Secret of the client `customer-ui`, used by the customer gateway |
| `INVENTORY_SERVICE_CLIENT_SECRET`, `RESERVATION_SERVICE_CLIENT_SECRET`, `SEARCH_SERVICE_CLIENT_SECRET`, `CACHE_UPDATER_CLIENT_SECRET`, `PRICING_SERVICE_CLIENT_SECRET`, `ANCILLARY_SERVICE_CLIENT_SECRET`, `INSURANCE_SERVICE_CLIENT_SECRET`, `DISCOUNT_SERVICE_CLIENT_SECRET` | Secrets of the clients of the services that call other services |
| `KEYCLOAK_TEST_USER_PASSWORD` | Password of the test users |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | The OAuth client of the Google application, for customer sign-in through Google. Without them the Google button leads to an error page at Google |
| `ADMIN_UI_URL`, `CUSTOMER_UI_URL` | Address of each UI, for the return address after sign-in. Default: `http://localhost:8090` and `http://localhost:8080` |

## Roles and test users

| User | Realm role | Country |
|---|---|---|
| `super-admin` | `super-admin` | All |
| `admin-pl` | `country-admin` | `pl` |
| `admin-de` | `country-admin` | `de` |
| `customer` | `customer` | |

A country administrator's country is the user attribute `country`. Tokens carry the realm roles in
the claim `roles` and the country in the claim `country`.

## Clients

| Client | Used by | Grants |
|---|---|---|
| `admin-ui` | The admin gateway | Sign-in of administrators (authorization code with PKCE) |
| `customer-ui` | The customer gateway | Sign-in of customers, and a token of its own without a role for the calls of a visitor (client credentials) |
| `inventory-service`, `reservation-service`, `search-service`, `cache-updater`, `pricing-service`, `ancillary-service`, `insurance-service`, `discount-service` | The service of the same name, for its calls to other services | A token with the realm role `service` (client credentials) |

Every access token names the audience `car-rental-api`, and the services accept no token without
it. The services check the signature with the keys of the realm, the issuer
`http://localhost:8180/realms/car-rental`, the expiry, the roles and the country.

## Customer sign-in through Google

The realm has the identity provider `google`. Every user who signs in through it becomes a Keycloak
user with the realm role `customer`, given by a mapper of the provider. Administrators never get
this role. The test user `customer` stands in for a Google account in development and tests.

To turn Google on, register an application once:

1. In the [Google Cloud Console](https://console.cloud.google.com/apis/credentials), create a
   project, configure the OAuth consent screen as *External* with the scopes `openid`, `email` and
   `profile`, and add your Google account as a test user.
2. Create an *OAuth client ID* of the type *Web application* with the authorised redirect URI
   `http://localhost:8180/realms/car-rental/broker/google/endpoint`.
3. Put its client ID and secret into `.env` as `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`, and
   recreate Keycloak's database once with `docker compose down -v`, so that the realm is imported
   with them.

