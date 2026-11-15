# Car Rental System: Architecture

Last updated: 2026-11-10

This document describes the design in force. It is written from the decisions recorded in [PROJECT_NOTES.md](PROJECT_NOTES.md), which keeps the market analysis and the history of how each decision was reached. Where the two differ, this document applies.

## Contents

1. Purpose and scope
2. Technology stack
3. System overview
4. Services
5. Countries as tenants
6. Domain model
7. Capacity rule
8. Reservation life cycle
9. Customer flow
10. Pricing
11. Identity and access
12. Events
13. Reference data cache
14. Administration
15. Service design rules
16. Non-functional requirements
17. Testing
18. Repository and delivery
19. Build order

## 1. Purpose and scope

### The assignment

Design and implement a simulated Car Rental system using object-oriented principles.

- The system should allow reservation of a car of a given type at a desired date and time for a given number of days.
- There are 3 types of cars (Sedan, SUV and van).
- The number of cars of each type is limited.
- Use unit tests to prove the system satisfies the requirements.

### What is built

The project deliberately goes beyond the assignment and is built the way rental operators build such systems: microservices, a database, events, a customer UI and an admin UI. The assignment's requirements do not depend on that infrastructure:

| Assignment requirement | Where it is satisfied | How it is proven |
|---|---|---|
| Reserve a car of a given type at a date and time for a number of days | The capacity rule in the domain of `inventory-service`, and the reservation in `reservation-service` | Unit tests of the domain, without Spring or a database |
| Three types of cars | Seed data: Sedan, SUV and van | A test that the seed contains exactly these types |
| The number of cars of each type is limited | Stock per location, type and class, and the capacity rule | Unit tests, plus an integration test with concurrent requests for the last car |
| Unit tests prove the requirements | Every requirement maps to named unit tests (NFR-24) | The test report in CI |

### In scope

- Reservations by car type and class, at a location, for a period.
- Several countries, several locations per country, and one-way rentals within a country.
- Search for available cars, insurance options, extras, prices and discounts.
- Customer sign-in, a driver profile, and an admin UI.
- Events for usage analytics.

### Out of scope

- Online payment and card data. Payment is post-pay at the pick-up location.
- Assigning a specific vehicle to a reservation.
- Late returns, and changing a reservation after it is made.
- Additional drivers.
- A service that stores or reports analytics events.
- High availability, backups and disaster recovery.
- Distributed tracing and dashboards.

## 2. Technology stack

| Area | Choice | Version on 2026-11-10 |
|---|---|---|
| Language | Java | JDK 25 (LTS) |
| Framework | Spring Boot | 4.1.2 |
| Build | Maven with Maven Wrapper | 3.6.3 or later |
| Database | PostgreSQL | 18 |
| Database access | Spring `JdbcClient` with explicit SQL, or Spring Data JDBC where the data is simple | Managed by Spring Boot |
| Migrations | Flyway | 12.4.0, managed by Spring Boot |
| Cache | Redis | To be pinned when added |
| Events | Kafka in KRaft mode | Client 4.2.2, managed by Spring Boot |
| Identity | Keycloak | 26.8.2 |
| Entry point | Spring Cloud Gateway on Spring Boot 4.0.9 | Spring Cloud 2025.1.4, gateway 5.0.4. It moves to Spring Boot 4.1 with Spring Cloud 2026.0 |
| Frontends | React, TypeScript, Vite, React Router, TanStack Query, MUI | 19.3.1, 5.9.3, 8.3.8, 8.5.1, 5.107.1, 9.5.0 |
| Backend tests | JUnit, Testcontainers | Testcontainers 2.0.5 |
| Frontend tests | Vitest, React Testing Library, Playwright | 5.1.0, 16.3.3, 1.65.0 |
| Packaging | Docker, Docker Compose | One image per deployable part |
| Maven `groupId` and base package | `psn.car.rental` | |

The frontend versions were checked when the admin UI was scaffolded. TypeScript 7 was replaced by 5.9.3, because the linter and the OpenAPI type generator do not support TypeScript 7 yet.

## 3. System overview

```
 User UI (React)                         Admin UI (React)
       |                                        |
       +-------------------+--------------------+
                           |
                     API gateway  <-- checks tokens -->  Keycloak
                           |                             (Google, Apple, Facebook)
   +-----------+-----------+-----------+-----------+-----------+
   |           |           |           |           |           |
 location   car-type    search     customer    pricing    reservation
 (info)     (info)         |           |        |  |  |        |
   |           |           |           |        |  |  |        |
   +--Redis----+           |           |        |  |  |        |
        ^                  |           |        |  |  |        |
   cache-updater           +--------> inventory <--+--+--------+
        ^                                          |  |
        | events                              insurance  ancillary  discount
        |                                         |
 administration                          insurance-provider-mock

 PostgreSQL: one server, one database per service, one schema per country
 Kafka: reference data events and analytics events
```

- **PostgreSQL** is the source of truth.
- **Kafka** is the only event channel.
- **Redis** is only a read cache for reference data. Reservations and stock never use it.

## 4. Services

Service names marked with * are working names.

| Service | Responsibility | Owns data | Per country |
|---|---|---|---|
| `api-gateway` | The entry point of a UI: sign-in, the session, token and role checks, the country of an administrator, routing, correlation ID. It runs twice, once per UI, so public and admin routes are apart | Sessions in Redis | No |
| `administration-service` | Reference data: countries with currencies, locations, car types, car classes. Publishes reference data events | Yes | Locations per country, the rest shared |
| `info-services/location` | Returns the served locations. Reads Redis only | No | Reads all countries |
| `info-services/car-type` * | Returns car types and classes. Reads Redis only | No | Shared data |
| `cache-updater` * | Writes reference data from events into Redis. Loads everything at startup through the API of `administration-service` | No | Keys carry the country |
| `inventory-service` | Stock, holds, transfers and registered returns. The only place where the capacity rule lives | Yes | Yes |
| `reservation-service` | Reservations, their options, their stored price and their state | Yes | Yes |
| `search-service` | Finds available car types and classes for a location and period by asking `inventory-service` | No | Follows the pick-up location |
| `customer-service` * | The driver profile: date of birth and licence country, linked to the Keycloak user | Yes | No |
| `insurance-service` * | Insurance options, and insurance prices obtained from the pricing provider | Yes | Options shared |
| `insurance-provider-mock` * | Stands in for the third-party insurance pricing service | Configuration only | No |
| `ancillary-service` * | Extras, their prices, and the one-way fee | Yes | Yes |
| `pricing-service` | Daily rental rates, public list prices, and the final price calculation | Yes, the rates | Yes |
| `discount-service` * | Discount rules | Yes | Yes |

Frontends:

| Frontend | Purpose | Address |
|---|---|---|
| `ui` | The customer application | Its own URL |
| `admin-ui` * | The administrator application | A separate URL, not combined with the customer UI |

Calls between services are synchronous REST calls with a timeout of 2 seconds. A failed dependency gives a clear error and never a hanging request.

## 5. Countries as tenants

- **A tenant is a country.** For a customer the country makes no difference. For an administrator it is a boundary.
- **Storage:** one PostgreSQL server, one database per service, and inside each per-country service one schema per country with identical tables.
- **Migrations:** Flyway applies the same migrations to every country schema. Adding a country means creating its schemas, running the migrations and loading its seed data.
- **Choosing the tenant:** for a customer request it follows from the country of the pick-up location. For an administrator request it comes from the country in the administrator's token. The gateway passes it on, and each service selects the schema from it.
- **The list of countries follows the administration service.** Each service serves the countries that have a schema in its database, loads the list from the administration service at startup, and then follows the country events. A new country gets its schema at once. An inactive country accepts no new reservations, and its existing reservations keep working.
- **The tenant reaches a service in the `X-Country` header.** For a country administrator the admin gateway sets it from the token and refuses a request for another country. A super administrator names the country. A customer request names the country of its location. A request without a served country is rejected with HTTP 400. The schema of a country is `tenant_<code>`, for example `tenant_pl`.
- **No operation spans two tenants.** One-way rentals and transfers stay within one country.
- **Shared data:** countries with their currencies, car types and car classes are the same everywhere and are changed only by a super administrator.
- **Per-country data:** locations, stock, rates, extras and their prices, the one-way fee, discount rules and reservations. Each country has its own list of extras, managed by that country's administrator.
- **Insurance options** (basic and full) are shared by all countries.
- **The location info service returns the locations of all countries** in one answer, because a visitor has no country before choosing a pick-up location.
- **Not tenant data:** customer accounts and driver profiles, because a customer can rent in any country.
- **Redis keys and Kafka events carry the country,** so data of two countries never mixes.

## 6. Domain model

| Term | Meaning |
|---|---|
| Country | A tenant. It has one currency |
| Location | A place where cars are picked up and returned. It belongs to a country and has a city, coordinates and a time zone |
| Car type | A category of car, stored as reference data. Seed data: Sedan, SUV, van |
| Car class | A level within a type, for example Economy, Comfort, Premium. Stored as reference data |
| Stock | The number of cars that belong to a location now, per type and class |
| Hold | One car kept for a reservation in `inventory-service` |
| Reservation | A customer's booking of one car of a type and class, from a pick-up location to a return location, for an interval |
| Interval | The half-open period `[start, start + N days)`. One day is 24 hours |
| One-way rental | A reservation whose return location differs from its pick-up location |
| Transfer | An administrator moving cars between two locations of one country |
| Extra | An ancillary product: equipment such as a child seat, or a service such as road toll payment |
| Quote | A calculated price with every line, the discount and the total |

Rules that apply to every reservation:

- The start is not in the past, evaluated in the time zone of the pick-up location.
- The rental lasts from 1 to 30 days. The maximum is configuration.
- The driver is at least 21 years old on the start date. The minimum is configuration.
- The return location is in the same country as the pick-up location.
- A reservation always has a car class. The class is optional only while searching.
- Car types and classes are data, not code. Adding one needs no code change.
- A type, class or location that is deactivated accepts no new reservations. Existing reservations stay valid. Reference data is never deleted through the API, only deactivated, because other services may refer to it.

Dates and times are entered and shown in the local time of the location and stored in UTC. Code reads the current time from an injected `Clock`.

## 7. Capacity rule

The capacity rule lives only in the domain of `inventory-service`.

- **Stock** is counted per location, car type and car class.
- **A same-location reservation** uses one car during its interval and gives it back at its planned end.
- **A one-way reservation** uses one car of the pick-up location from its start onward, with no end. For the pick-up location that car is gone.
- **An arrival counts only when it has happened.** A car joins the stock of the return location when an administrator registers the actual return. A transfer works the same way: the car leaves the source stock at the departure time and joins the target stock when its arrival is registered. The planned arrival time is information only.
- **The rule:** at every moment from now on, the cars in use at a location must not exceed its stock. A hold, a transfer or a stock reduction that would break this is rejected.

Consequences:

- Cancellation before the start is always possible, because no reservation relies on a car that has not arrived.
- Cars on their way are not bookable until their arrival is registered. A location can therefore look emptier than it will be.
- Equipment such as child seats is not stock-controlled. Each extra only has a maximum quantity per reservation.

### Holds

Stock and reservations are in different services, so a reservation is made in two steps that cannot overbook:

1. `reservation-service` asks `inventory-service` for a hold, identified by the reservation ID.
2. `inventory-service` locks the stock row with `SELECT ... FOR UPDATE`, checks the capacity rule and stores a provisional hold, all in one transaction. If no car is free, it refuses.
3. `reservation-service` stores the reservation as confirmed and confirms the hold.
4. If step 3 fails, `reservation-service` releases the hold.

- A hold request repeated with the same reservation ID returns the existing hold.
- A provisional hold that is not confirmed within 2 minutes is released automatically, so a crash between steps 2 and 3 cannot keep a car blocked. The time limit is configuration.
- Holds for one location, type and class are processed one after another because of the row lock. This is acceptable at the expected load.

## 8. Reservation life cycle

| State | Meaning | Set by | Effect on inventory |
|---|---|---|---|
| Confirmed | The hold exists and the reservation is stored | The customer, by reserving | The hold is confirmed |
| Cancelled | Cancelled before the start | The customer | The hold is released |
| Picked up | The car was handed over | An administrator | None |
| Returned | The car is back | An administrator | For a one-way rental, the car joins the stock of the return location |
| No-show | The customer did not come | An administrator | The hold is released |

Returns of same-location rentals do not need to be registered for the capacity rule, because their planned end is used.

Timing rules:

- A reservation can be cancelled until its start.
- A no-show can be marked from the start onward.
- A pick-up can be registered at any time while the reservation is confirmed.
- A reservation whose hold expired before it was confirmed, which can only happen after a crash, is cancelled.

A change of state and the inventory action it needs (confirm, release or return) are stored in one transaction. The action is carried out right after, and a job retries it if the inventory does not answer.

## 9. Customer flow

| Step | Screen | What happens | Login | Services |
|---|---|---|---|---|
| 1 | Search | The customer picks the pick-up location, the return location, the start, the number of days, a car type and optionally a class | No | `location`, `car-type`, `search-service`, `inventory-service` |
| 2 | Selection | The customer chooses the car class, extras and an insurance option, and sees list prices | No | `pricing-service`, `ancillary-service`, `insurance-service` |
| 3 | Sign in | The customer signs in. On the first sign-in they enter the date of birth and the licence country | Becomes yes | Keycloak, `customer-service` |
| 4 | Final price | The quote is shown with every line, the discount and the total | Yes | `pricing-service` |
| 5 | Reservation | The customer confirms, and the reservation is created | Yes | `reservation-service`, `inventory-service` |
| 6 | Confirmation | The amount due at pick-up is shown | Yes | None |

- There is no country selector. The pick-up list shows the locations of all active countries, grouped by country, and the country follows from the chosen location.
- The return location is offered only from the country of the pick-up location.
- The search is kept in the address bar, so a result can be reloaded or shared.
- Search without a class shows every available class of the chosen type, each with its count and its daily list rate.
- What the customer chose in steps 1 and 2 is kept while they sign in.
- Search applies the same limits as a reservation, and lists only type and class combinations with at least one free car, of active types and classes, ordered by name.
- Search is advisory. Availability is checked again when the hold is requested, and the UI shows a clear message if the car was taken in between.
- A quote reserves nothing.

### Creating a reservation

`reservation-service` does the following for one request, which carries an `Idempotency-Key` header:

1. Validates the request and the rules of section 6.
2. Asks `pricing-service` for the price for the customer, with its own token. The pricing service reads the driver profile from `customer-service` and refuses a driver who is too young; its refusals are passed on unchanged. A price sent by a browser is never used.
3. Compares the price with the total the customer saw, which the request carries. When they differ, nothing is reserved, and the answer is 409 `/problems/price-changed` with the new price for the customer to confirm.
4. Requests the hold from `inventory-service`.
5. Stores the reservation with the price, the currency, the insurance option, the extras and the payment method "pay at pick-up", then confirms the hold.
6. Records the analytics event in its outbox.

A repeated request with the same idempotency key returns the first result and creates nothing new, without asking for the price again. The key is stored in the same transaction as the reservation. The customer UI uses one key for each price it shows.

- **A customer's reservations** are listed across all countries, latest start first. Each reservation names its country, which every later request about it names too.
- **The admin UI** lists the reservations of a country by pick-up location, and staff register the pick-up, the return and a no-show there.

## 10. Pricing

All prices of a reservation are in the currency of the pick-up country. Nothing is converted. Prices include tax, and tax is not shown separately.

| Line | Source | Calculation |
|---|---|---|
| Car rental | `pricing-service`: a daily rate per location, type and class | Rate multiplied by the number of days |
| Insurance | `insurance-service`, which asks the pricing provider | Price per day multiplied by the number of days |
| Extras | `ancillary-service`: a price per extra in the country | Per day or once per rental, multiplied by the quantity |
| One-way fee | `ancillary-service`: a fixed amount per country | Added when the two locations differ. The customer cannot remove it |
| Discount | `discount-service`: the rules of the country | Only the rule that is best for the customer applies |

- **Money** is stored as a decimal amount with a currency, never as a floating-point number. Each line is rounded half up to the minor unit of the currency.
- **List prices are public.** Anyone can read the daily rates and the prices of extras. Insurance is shown before login as its base price marked "from".
- **Daily rates.** There is one current rate per location, car type and car class, without seasons or validity dates. A car type and class without a rate at the pick-up location cannot be priced, so the search does not offer it, and the admin UI marks it. The search shows the daily rate of each offer.
- **Extras** have a code, a name, a kind (equipment or service), a price per day or per rental, and the most one reservation can have. Their number is not limited by any stock. They are never deleted, only switched off.
- **Amounts in the APIs** are decimal strings with as many fraction digits as the currency has minor units, such as `"149.00"`, so that no client turns them into floating-point numbers.
- **The currency of a country** comes from the administration service with the country list. `pricing-service` and `ancillary-service` keep it, so that a price is always in the currency of its country.
- **The final price requires a logged-in customer,** and so does a reservation.
- **The price is stored with the reservation.** Later price changes do not alter existing reservations.
- **The quote** comes from `POST /api/v1/quotes` of `pricing-service`, for a signed-in customer. It checks the rental like a reservation, reads the driver profile from `customer-service` with its own token, and asks `insurance-service`, `ancillary-service` and `discount-service` for their parts. A driver younger than the minimum age on the start day gets no price. When a service does not answer, the customer sees that the price is not available, and no price is invented.
- **In the customer UI** the insurance and the extras are chosen on the selection page at list prices and kept in the address bar. The final price is a page of its own with every line, the discount and the total due at pick-up.

### Insurance

- There are two options, basic and full. Basic is preselected, and every reservation has one.
- The price comes from a third-party pricing service. In this project `insurance-provider-mock` stands in for it, and `insurance-service` reaches it through an interface so that a real provider can replace it.
- `insurance-service` sends the option, the driver's age, the licence country and the rental country. The name and the date of birth are not sent.
- The mock returns a base price per day of 10 currency units for basic and 50 for full, multiplied by 1.5 for a driver under 25 and by 1.2 when the licence country differs from the rental country. Both factors can apply together. The factors are configuration of the mock.
- If the provider does not answer in time, the customer sees a clear error and no invented price.
- **The options** basic and full are stored in `insurance-service` with a name and a description of the cover; basic is preselected. They have no admin page.
- **The "from" price** before sign-in is the provider's base price: `insurance-service` asks without driver data, and the provider applies no factor. Every insurance price therefore comes from the provider.
- **The price for a driver** is asked only by other services, which pass the driver's age on the start of the rental and the licence country. The answer is a price per day in the currency of the rental country.
- **The currency** of the rental country comes from the country list of the administration service, which `insurance-service` follows and keeps.
- **The provider is a third party.** The mock checks its own API key instead of the system's tokens, and `insurance-service` calls it with a client that never sends the system's access token. The time limit is 2 seconds.

### Discount rules

- A rule has a condition and an effect, for example "3 or more extras: 10% off the extras". Rules are data that an administrator adds.
- Conditions: the number of extras, specific extras together, the rental length.
- Effects: a percentage or a fixed amount off, applied to the extras or to the total.
- A rule has a validity period and can be switched off.
- There is no free-form rule language.
- **A rule has one or more conditions,** and applies only when all of them hold. The number of extras counts their quantities.
- **`discount-service` chooses the discount.** The pricing service sends the basket it calculated: the day, the number of days, the total and the extras with their prices. The answer is the rule that saves the most, or none. Only other services may ask.
- **The validity period** is checked against the day the price is calculated, in the time zone of the pick-up location. Both days of the period are included.
- **A saving never exceeds** what it is taken off. A percentage is rounded half up to the minor unit. On a tie, the rule whose name comes first applies.
- **Rules are never deleted,** only switched off. Administrators of the country manage them in the admin UI.

## 11. Identity and access

- **Keycloak** is the identity server. It runs as a container with its own PostgreSQL database. Its realm configuration is in the repository and is imported at startup. Provider secrets come from environment variables.
- **Customers** sign in through Google, Apple or Facebook. The system stores no customer passwords. Google is set up first, and Apple and Facebook are added by configuration. The provider is a Keycloak identity provider whose client ID and secret come from the environment, and a mapper of the provider gives every user who signs in through it the realm role `customer`. Administrators never get this role.
- **Administrators** are Keycloak users with a username and a password.
- **Test users** in the realm make the system usable without provider secrets, for a reviewer and for the end-to-end tests. The test user `customer` stands in for a Google account.
- **The driver profile** is entered after the first sign-in, before the price and the reservation. The customer UI asks for it and then returns to the page where the sign-in started. `customer-service` keeps it under the user ID of Keycloak, the subject of the token, so a customer reaches only their own profile.
- **A reservation belongs to the customer who made it.** The reservation service stores the user ID from the token, and a customer reads and cancels only their own reservations. Another customer's reservation is answered as missing. Idempotency keys are kept per customer.
- **Two gateway instances.** The gateway runs once for the customer UI and once for the admin UI, from the same image with a different profile. Each has its own Keycloak client, its own cookies and only the routes of its UI, so an admin route cannot be reached through the customer UI.
- **Sign-in follows the backend-for-frontend pattern.** The gateway signs the user in through Keycloak and keeps the tokens in its session, stored in Redis so that the gateway itself keeps no state in memory (NFR-7). The browser holds only an HttpOnly, SameSite session cookie, requests that change data carry a CSRF token, and no token reaches JavaScript.
- **Services do not handle logins.** The gateway passes the user's token on, and the gateway and each service validate it and read the user ID, the roles and the country from it. A call from one service to another uses a token of the calling service.
- **Every call to a service carries a token.** A service accepts only a token that Keycloak signed with a key of the realm, that has not expired, that names the realm at `http://localhost:8180` as its issuer and that names the audience `car-rental-api`. Health checks and the API description are open.
- **A visitor's call carries the customer gateway's own token.** The gateway obtains it with the client credentials grant of `customer-ui`. It has no role, so it opens only the paths for everyone.
- **A service that calls another service uses its own Keycloak client**, `inventory-service`, `reservation-service`, `search-service` or `cache-updater`, with the realm role `service`. Paths meant only for other services, such as the reference data snapshot, availability and holds, need this role. The token is kept in memory until shortly before it expires. A missing token counts as an unreachable dependency.
- **Each service checks the roles and the country again,** with the same rules as the gateway, so a request that reaches a service another way is still refused. A country administrator's request for another country is refused with 403.
- **Only the UIs, the gateways and Keycloak are reachable from outside Docker.** For debugging, `compose.ports.yaml` publishes the ports of the services, which still need a token.

| Role | Can do |
|---|---|
| Visitor, not signed in | Read locations, car types and classes, search, and read list prices |
| Customer | Everything a visitor can, plus the driver profile, the final price, reserving, and cancelling their own reservations |
| Country administrator | Administer the data of one country. Refused for any other country |
| Super administrator | Administer all countries, create a country, and change countries, car types and car classes |

A customer account never gives access to the admin UI. Administrators cannot register themselves.

- **Roles are Keycloak realm roles:** `super-admin`, `country-admin`, `customer`, and `service` for the services' own clients. A country administrator's country is the user attribute `country`. Tokens carry the roles in the claim `roles` and the country in the claim `country`.
- **Keycloak's address.** The browser reaches Keycloak at `http://localhost:8180`, and the gateways at `http://keycloak:8080` inside Docker. Tokens name the browser's address as their issuer. The gateway therefore describes the Keycloak client itself instead of discovering it.
- **The realm** with its roles, clients, service clients and test users is in `infra/keycloak/car-rental-realm.json`. Client secrets and the test password come from the environment.

### Privacy

- Only the data needed for the rental is collected: the name and email from the provider, the date of birth and the licence country. The name and email stay in Keycloak and are read from the token. `customer-service` stores only the date of birth and the licence country.
- The driver's age is calculated on the rental start date and is not stored.
- Personal data is not written to logs and is not sent to Kafka. Analytics events carry an internal customer ID, an age band and a country.
- A customer can see only their own data.

## 12. Events

Kafka carries two kinds of events.

| Kind | Events | Published by |
|---|---|---|
| Reference data | Countries, locations, car types or car classes changed. The event contains the changed data | `administration-service` |
| Analytics | Search performed | `search-service` |
| Analytics | Price quoted | `pricing-service` |
| Analytics | Reservation created, rejected with its reason, cancelled | `reservation-service` |
| Analytics | Administrator actions: changes to reference data, stock, prices and discount rules, and transfers | The service that owns the changed data |

Rules:

- **Outbox:** an event is written to a table in the same transaction as the change it describes and sent to Kafka afterwards. An event is never lost and never describes a change that did not happen.
- **Idempotent consumers:** Kafka can deliver an event more than once, so handling it twice must be harmless.
- **Analytics never blocks business.** If Kafka is down, searching and reserving still work, and the events are sent later.
- **No consumer of analytics events is built.** They are published for later use.
- **One topic, `analytics`,** carries every analytics event, keyed by its country. Every event has the same envelope: `eventId`, `type`, `country`, `occurredAt`, `service`, `actor` and `data`.
- **Event types:** `search.performed`, `price.quoted`, `reservation.created`, `reservation.rejected` with its reason, `reservation.cancelled`, `reservation.picked-up`, `reservation.returned`, `reservation.no-show`, and the administrators' actions `admin.reference-data-changed`, `admin.stock-changed`, `admin.transfer-dispatched`, `admin.transfer-arrived`, `admin.rate-changed`, `admin.extra-changed`, `admin.one-way-fee-changed`, `admin.discount-rule-created` and `admin.discount-rule-changed`.
- **The actor is named by ID only:** a customer by the user ID in Keycloak, an administrator by the username, a service by its client. Names, email addresses and dates of birth are never in an event. Search events name no one.
- **The outbox is shared code** in `services/shared`: each service with a database has an `outbox` table and turns it on with `car-rental.outbox.enabled`. `search-service` has no database, so it sends its events straight to Kafka on a thread of their own; a search never waits, and an event that Kafka does not take is lost.
- **`price.quoted`** is recorded only for a quote a customer asks for. A quote that the reservation service asks for is described by the reservation events.

## 13. Reference data cache

1. An administrator changes reference data in the admin UI.
2. `administration-service` stores the change in PostgreSQL and records an event with the changed data.
3. `cache-updater` receives the event and writes the data to Redis.
4. `info-services/location` and `info-services/car-type` answer from Redis only.

- At startup `cache-updater` loads all reference data through the API of `administration-service`. It never reads another service's database.
- Redis layout: `ref:countries`, `ref:car-types` and `ref:car-classes` are hashes from code to JSON, `ref:locations:<country>` is a hash from location ID to JSON, and `ref:loaded` holds the time of the last full load. Without `ref:loaded` the info services treat the cache as empty.
- A full load replaces all keys in one Redis transaction. `cache-updater` checks every 30 seconds whether `ref:loaded` exists, and reloads if it does not.
- If Redis is down or empty, the info services answer 503, and `cache-updater` reloads the data within 30 seconds of Redis being back.
- The location info service also serves the list of countries. Lists contain only active items, while reading one item by its key also returns an inactive one.

## 14. Administration

The admin UI calls only its own address. nginx in Docker, and the Vite dev server during development, pass the API, sign-in and sign-out to the admin gateway, which passes each API path on to the service that owns the data, under an `/admin` path. The admin gateway checks the role and the country. The customer UI works the same way with the customer gateway.

| Function in the admin UI | Service |
|---|---|
| Countries and currencies, car types, car classes (super administrator only) | `administration-service` |
| Locations | `administration-service` |
| Stock per location, type and class | `inventory-service` |
| Transfers between locations, and registering their arrival | `inventory-service` |
| Registering a pick-up, a return and a no-show | `reservation-service`, which informs `inventory-service` |
| Extras, their prices and the one-way fee | `ancillary-service` |
| Daily rental rates | `pricing-service` |
| Discount rules | `discount-service` |

A stock reduction that would break the capacity rule is rejected.

## 15. Service design rules

- **Hexagonal structure.** Each service has a domain without Spring or database code, an application layer, and adapters for REST, PostgreSQL, Kafka and calls to other services.
- **The domain is proven by plain unit tests.** This is where the assignment's requirements are tested.
- **Database access** uses Spring's `JdbcClient` with explicit SQL wherever locking or tenant selection matters, as in `inventory-service`. Spring Data JDBC repositories may be used in services whose data is simple.
- **OpenAPI first.** Each service has a hand-written `openapi.yaml` next to its code, served at `/openapi.yaml`. No server code is generated from it. The TypeScript types of both frontends are generated from it.
- **REST with JSON** under a versioned path such as `/api/v1`. Errors use the Problem Details format (RFC 9457).
- **One shared module** only for the error format, the correlation ID and the token checks. It also passes the correlation ID and the service's own token on in outgoing HTTP calls. Each service declares its own access rules. Domain code is never shared between services. The tenant code (country, schema selection, migrations) is copied in each service.
- **Service addresses** are Docker Compose service names set through environment variables. There is no discovery server and no configuration server.
- **Schema changes** are Flyway migrations that run at startup.
- **No secrets in the repository.** Credentials come from environment variables, and `.env.example` documents them.

## 16. Non-functional requirements

The numbers are sized for a low-load service on one developer machine.

| ID | Requirement |
|---|---|
| NFR-1 | No overbooking: at no moment do the cars in use at a location exceed its stock, including under concurrent requests |
| NFR-2 | Creating a reservation is strongly consistent: a confirmed reservation and its hold are already stored in PostgreSQL |
| NFR-3 | Creating a reservation is idempotent: a request repeated with the same `Idempotency-Key` does not create a second reservation |
| NFR-4 | All timestamps are stored in UTC, and the code reads the current time from an injected `Clock` |
| NFR-5 | Expected load: up to 50 concurrent users and 20 requests per second |
| NFR-6 | At that load, 95% of API responses arrive within 300 ms on a developer machine |
| NFR-7 | Services keep no state in memory between requests, so more instances can be started without code changes |
| NFR-8 | Every service exposes liveness and readiness health checks, and Docker Compose uses them to order startup |
| NFR-9 | Calls between services have a timeout of 2 seconds, and a failed dependency produces a clear error |
| NFR-10 | Services shut down gracefully and finish requests that are in progress |
| NFR-11 | Data survives a restart: PostgreSQL uses a Docker volume |
| NFR-12 | All input is validated at the API boundary, and invalid input returns HTTP 400 with a description |
| NFR-13 | SQL uses bound parameters only |
| NFR-14 | No secrets in the repository |
| NFR-15 | Containers run as a non-root user |
| NFR-16 | CORS allows only the origins of the two frontends |
| NFR-17 | Dependabot reports vulnerable dependencies |
| NFR-18 | Logs are structured JSON written to standard output |
| NFR-19 | Each request carries a correlation ID that is passed between services and written to every log line |
| NFR-20 | Each service exposes metrics through Spring Boot Actuator |
| NFR-21 | APIs are REST with JSON under a versioned path |
| NFR-22 | Errors use the Problem Details format (RFC 9457) |
| NFR-23 | Each service publishes an OpenAPI description |
| NFR-24 | Every requirement of the assignment maps to at least one named unit test |
| NFR-25 | Line coverage is at least 90% for each domain and at least 80% for each service overall, checked in CI |
| NFR-26 | The domain has no dependency on Spring or the database |
| NFR-27 | Database schema changes are versioned Flyway migrations that run at startup |
| NFR-28 | Code formatting and linting are checked in CI, for Java and TypeScript |
| NFR-29 | Running the system needs only Docker: `docker compose up` starts everything |
| NFR-30 | Building and testing needs only JDK 25 and Docker, thanks to Maven Wrapper |
| NFR-31 | The README explains how to build, test and run in under 5 minutes of reading |
| NFR-32 | The UIs support the latest Chrome, Firefox and Safari on desktop, Chrome on Android and Safari on iOS |
| NFR-33 | The layout is responsive and does not break in a phone browser: from 360 px width there is no horizontal scrolling and no overlapping or cut-off content, and every function is available |
| NFR-34 | Forms are operable by keyboard, and every field has a label |
| NFR-35 | The UI language is English only |
| NFR-36 | On a phone, text is readable without zooming, touch targets are at least 44 by 44 px, and the date and time picker works by touch |
| NFR-37 | Playwright runs the main reservation flow on a phone-sized viewport as well as on desktop |
| NFR-38 | Data of one country is never readable or changeable through a request for another country |

## 17. Testing

| Level | Tools | What it proves |
|---|---|---|
| Unit | JUnit, no Spring | The domain rules, including every requirement of the assignment |
| Integration | Testcontainers with real PostgreSQL, and later Redis, Kafka and Keycloak | SQL, migrations, tenant separation, and that concurrent requests cannot take the last car |
| Frontend | Vitest, React Testing Library | Components and forms |
| End to end | Playwright, on desktop and phone viewports | The whole customer flow and the admin functions |

- The concurrency test runs against real PostgreSQL, because the row lock cannot be proven with mocks or an in-memory database.
- There are no contract tests between services. End-to-end tests and the OpenAPI descriptions cover the interaction.

## 18. Repository and delivery

One private repository: https://github.com/siarhei-ka/car-rental

```
car-rental/
├── README.md
├── docs/
│   ├── ARCHITECTURE.md
│   └── PROJECT_NOTES.md
├── pom.xml                      parent POM of the Maven multi-module build
├── mvnw, .mvn/                  Maven Wrapper
├── services/
│   ├── api-gateway/
│   ├── administration-service/
│   ├── info-services/
│   │   ├── location/
│   │   └── car-type/
│   ├── cache-updater/
│   ├── inventory-service/
│   ├── reservation-service/
│   ├── search-service/
│   ├── customer-service/
│   ├── insurance-service/
│   ├── insurance-provider-mock/
│   ├── ancillary-service/
│   ├── pricing-service/
│   ├── discount-service/
│   └── shared/                  error format, correlation ID, token checks, outbox and analytics events
├── ui/
├── admin-ui/
├── compose.yaml
├── .github/
│   ├── workflows/
│   └── dependabot.yml
└── .gitignore, .editorconfig, .env.example
```

Each service and each frontend has its own Dockerfile and image. PostgreSQL, Redis, Kafka and Keycloak use official images.

- **Branches:** `main` is always green. Work happens on short-lived branches and arrives through squash-merged pull requests.
- **Commits:** Conventional Commits. The commit message is agreed before each commit.
- **CI with GitHub Actions:** a backend workflow runs `./mvnw verify`, a UI workflow runs lint, type-check, Vitest and build, an end-to-end workflow starts the system with Docker Compose and runs Playwright, and an image workflow builds every Docker image.
- **Dependabot** covers Maven, npm, GitHub Actions and Docker.

## 19. Build order

| Step | Content | Result |
|---|---|---|
| 1 | Repository skeleton, CI, `inventory-service` and `reservation-service` with the full capacity rule, holds, the reservation life cycle, country schemas, unit tests and Testcontainers tests. Stock and reference data come from seed data | The original assignment is satisfied and proven by tests |
| 2 | `administration-service`, the info services, `cache-updater`, Redis, Kafka, and the admin UI | Reference data and stock are managed by an administrator |
| 3 | `search-service` and the customer UI with the search and selection pages | A visitor can search and choose |
| 4 | Keycloak, the API gateway and `customer-service` | Customers and administrators sign in |
| 5 | `insurance-service` with its mock, `ancillary-service`, `pricing-service` and `discount-service` | The final price is calculated |
| 6 | The whole flow end to end, analytics events, and the non-functional requirements checked | The complete system |

In steps 1 to 3 the customer and admin APIs are not protected, because Keycloak and the gateway arrive in step 4. The system is not exposed outside the development machine during these steps.

Docker Desktop is installed on the development machine (Docker 29.9.3 and Docker Compose 5.7.0 on 2026-11-10). It is given 8 GB of memory by default, which is enough for step 1 and has to be raised before the whole system runs together.

Before step 4: a Google application must be registered by the project owner. Until then the Keycloak test users are enough.
