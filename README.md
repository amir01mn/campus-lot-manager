# CampusPark

**Hourly campus parking with live spot sensors: drivers book and pay by the hour, and managers watch occupancy, revenue and alerts in real time.**

[![CI](https://github.com/amir01mn/campus-lot-manager/actions/workflows/ci.yml/badge.svg)](https://github.com/amir01mn/campus-lot-manager/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A full-stack portfolio project: a Spring Boot 3.5 / Java 17 REST API on PostgreSQL 16 and a React 19 + TypeScript
single-page app, packaged with Docker Compose and tested end to end in GitHub Actions.

![Manager dashboard with bookings per day, revenue by user type and utilisation per lot](docs/screenshots/08-manager-dashboard.png)

## Features

- **Book by the hour.** Pick a lot, a time window and a spot on a live map, see the price before you pay, then
  extend, cancel (with the refund rule spelled out) or check out. Every booking has a receipt.
- **Tiered pricing.** Your tier (student $5, faculty $8, staff $10, visitor $15 per hour) comes from the university
  directory, not a dropdown. Billing rounds up to the started hour, and a one-hour deposit is charged at booking.
- **Live spot sensors.** Sensors report occupancy through a key-authenticated endpoint. The app raises
  *unauthorized parking* and *overstay* alerts and auto-completes bookings that have ended.
- **Manager tools.** A dashboard (occupancy now, bookings per day, revenue by tier, per-lot utilisation), a searchable
  bookings table, and lot and spot controls (open/close, disable, flag for maintenance).
- **Admin.** The super manager creates manager accounts with one-time generated passwords.
- **In-app notifications** for booking changes and alerts.
- **Accessible, responsive UI** with dark mode, keyboard-navigable spot grid, and campus-time (America/Toronto)
  display regardless of the browser's zone.

<details>
<summary><strong>Screenshot gallery</strong> (16 screens: driver, manager, admin, mobile and dark mode)</summary>

| | |
|---|---|
| <img src="docs/screenshots/01-login.png" width="420" alt="Sign-in with one-click demo accounts"><br>Sign-in with one-click demo accounts | <img src="docs/screenshots/02-register-password-policy.png" width="420" alt="Registration with live password policy"><br>Registration with live password policy |
| <img src="docs/screenshots/03-lots-overview.png" width="420" alt="Lots overview"><br>Lots overview with live availability | <img src="docs/screenshots/04-lot-detail-spot-grid-quote.png" width="420" alt="Spot grid and price quote"><br>Spot grid, time window and price quote |
| <img src="docs/screenshots/05-my-bookings.png" width="420" alt="My bookings"><br>My bookings | <img src="docs/screenshots/06-receipt.png" width="420" alt="Receipt"><br>Receipt with every payment |
| <img src="docs/screenshots/07-cancel-refund-dialog.png" width="420" alt="Cancel dialog"><br>Cancel dialog explaining the refund rule | <img src="docs/screenshots/08-manager-dashboard.png" width="420" alt="Manager dashboard"><br>Manager dashboard |
| <img src="docs/screenshots/09-notifications.png" width="420" alt="Notifications"><br>Notifications and sensor alerts | <img src="docs/screenshots/10-manager-bookings-table.png" width="420" alt="All bookings"><br>All bookings, filterable |
| <img src="docs/screenshots/11-lot-management.png" width="420" alt="Lot management"><br>Lot and spot controls | <img src="docs/screenshots/12-admin-create-manager.png" width="420" alt="Create manager"><br>Super manager creates a manager |
| <img src="docs/screenshots/13-mobile-lot-detail.png" width="200" alt="Mobile lot detail"> <img src="docs/screenshots/14-mobile-my-bookings.png" width="200" alt="Mobile bookings"><br>Mobile | <img src="docs/screenshots/15-dark-dashboard.png" width="420" alt="Dark dashboard"><br>Dark mode |
| <img src="docs/screenshots/16-dark-lot-detail.png" width="420" alt="Dark lot detail"><br>Dark mode, lot detail | |

</details>

## Architecture

```mermaid
flowchart LR
    browser["Browser<br/>React + TypeScript SPA"]
    sensors["Spot sensors<br/>(demo: built-in simulator)"]
    subgraph web["frontend container"]
        nginx["nginx<br/>static assets, SPA fallback,<br/>reverse proxy"]
    end
    subgraph api["backend container: Spring Boot 3.5"]
        rest["REST controllers<br/>Spring Security + JWT"]
        services["Domain services<br/>bookings, pricing, payments, lots"]
        jobs["Scheduled jobs<br/>auto-checkout, overstay detection"]
        events["Domain events →<br/>AFTER_COMMIT listeners<br/>(notifications, EmailSender)"]
    end
    db[("PostgreSQL 16<br/>Flyway migrations,<br/>exclusion constraints")]

    browser -- "HTTP :3000" --> nginx
    nginx -- "/api, /swagger-ui, /v3/api-docs" --> rest
    sensors -- "POST /api/sensors/readings<br/>X-Sensor-Key" --> rest
    rest --> services
    jobs --> services
    services -- "publish" --> events
    services --> db
    events --> db
```

- The browser only talks to nginx. nginx serves the static bundle and forwards the API, so calls are same-origin and
  the backend port is never published.
- The backend is a layered, feature-packaged Spring Boot app: `auth`, `user`, `lot`, `booking`, `payment`, `sensor`,
  `notification` and `analytics`. Controllers take and return DTOs (Java records), and errors are RFC 7807
  `ProblemDetail`s.
- Payments go through a `PaymentGateway` interface. The only implementation is a simulated gateway, so a real
  provider can be plugged in later.

## Tech stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.5 (Web, Data JPA, Security, OAuth2 Resource Server for HS256 JWTs, Validation, Actuator), Flyway, springdoc-openapi |
| Database | PostgreSQL 16 (`btree_gist` exclusion constraints, `timestamptz`, `numeric(10,2)` money) |
| Frontend | React 19, TypeScript 5.9 (strict), Vite 8, React Router 8, TanStack Query 5, Tailwind CSS 4, Recharts 3, lucide icons |
| Testing | JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers, Vitest 5, Testing Library, MSW 3, Playwright |
| Delivery | Docker multi-stage builds (layered Spring Boot jar on a JRE, unprivileged nginx), Docker Compose, GitHub Actions, Dependabot |

## Quick start

You only need Docker (with Compose v2):

```bash
git clone https://github.com/amir01mn/campus-lot-manager.git
cd campus-lot-manager
docker compose up --build
```

Then open **http://localhost:3000**. The first build takes a few minutes; later starts take seconds. No `.env` is
needed. The defaults run the `demo` profile, which seeds:

- the demo accounts below;
- about 140 background drivers with roughly two months of bookings and payments;
- bookings for now and the coming week;
- a sensor simulator that keeps the map alive.

The login page has one-click buttons for every demo account.

| Account | Email | Password | What to try |
|---|---|---|---|
| Student ($5/h) | `student.demo@my.yorku.ca` | `Demo#2026` | Book a spot, extend, cancel, receipts |
| Faculty ($8/h) | `faculty.demo@yorku.ca` | `Demo#2026` | Same flows at the faculty rate |
| Staff ($10/h) | `staff.demo@yorku.ca` | `Demo#2026` | Same flows at the staff rate |
| Visitor ($15/h) | `visitor.demo@example.com` | `Demo#2026` | Same flows at the visitor rate |
| Manager | `manager.demo@yorku.ca` | `Demo#2026` | Dashboard, all bookings, lot and spot controls, alerts |
| Super manager | `admin@campus-parking.local` | `ChangeMe!2024` | Everything above plus creating managers |

The super manager is created from `APP_SUPER_MANAGER_EMAIL` / `APP_SUPER_MANAGER_PASSWORD`. **Change these, and every
other secret in [`.env.example`](.env.example), for anything beyond a local demo.**

To configure the stack, copy `.env.example` to `.env` and edit it. Compose reads it automatically.

```bash
docker compose down        # stop (data is kept in the postgres-data volume)
docker compose down -v     # stop and wipe the database; the next start seeds a fresh demo
```

## Local development

Run the database in Docker and the apps from source (JDK 17, Node 22.22 or newer).

```bash
docker compose up -d postgres      # PostgreSQL 16 on 127.0.0.1:5432
```

**Backend** (http://localhost:8080):

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # Windows: mvnw.cmd ...
```

**Frontend** (http://localhost:5173, proxying `/api` to the backend on :8080):

```bash
cd frontend
npm ci
npm run dev
```

Useful frontend scripts: `npm run lint`, `npm run typecheck`, `npm test`, `npm run build`, and `npm run gen:api`
(regenerates the TypeScript API types from the running backend's OpenAPI document). See
[`backend/README.md`](backend/README.md) for backend configuration.

## Testing

| Suite | Command | What it covers |
|---|---|---|
| Backend: 184 tests | `cd backend && ./mvnw verify` | Unit tests (pricing, password policy, reference codes, the production config guard, the demo-data planner) and 15 integration test classes against a real PostgreSQL 16 in **Testcontainers**: auth, bookings, payments, sensors, scheduled jobs, notifications, analytics and admin. A **concurrency test** races two users for the same spot and expects exactly one 201 and one 409. |
| Frontend: 44 tests | `cd frontend && npm test` | Vitest + Testing Library + MSW: booking form and quote, spot grid states and keyboard navigation, login, password policy, KPI tiles, booking rules, DST-safe campus time helpers |
| End-to-end | `cd frontend && E2E_BASE_URL=http://localhost:3000 npm run e2e` | Playwright against the running Compose stack: sign in, book a spot, find it in My bookings, cancel it with a refund, check the receipt |

The integration tests need Docker running. `npm run screenshots` regenerates the images in `docs/screenshots/`
against a running demo stack (set `E2E_BASE_URL` the same way).

CI ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)) runs the backend and frontend suites in parallel. It then
starts the full stack with `docker compose up --wait`, runs Playwright against it, and builds both images.

## API documentation

Swagger UI is served through the same proxy at **http://localhost:3000/swagger-ui.html**, and the OpenAPI document is
at `/v3/api-docs`. To try authenticated endpoints:

1. Call `POST /api/auth/login` with a demo account.
2. Paste the `accessToken` into **Authorize**.

Sensor ingestion uses the `X-Sensor-Key` header instead of a JWT. The demo key is `dev-sensor-key`:

```bash
curl -X POST http://localhost:3000/api/sensors/readings \
  -H "X-Sensor-Key: dev-sensor-key" -H "Content-Type: application/json" \
  -d "{\"readings\":[{\"sensorId\":\"SNS-A-001\",\"occupied\":true,\"timestamp\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"}]}"
# {"accepted":1,"ignoredStale":0,"ignoredFuture":0,"unknownSensors":[]}
```

## Engineering highlights

- **Double booking is impossible at the database level.** Two Postgres exclusion constraints reject overlapping
  `CONFIRMED` bookings on the same spot and for the same user:
  `EXCLUDE USING gist (spot_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&) WHERE (status = 'CONFIRMED')`.
  Violations (SQLSTATE `23P01`) map to HTTP 409, so the rule holds even if application checks are bypassed.
- **A deadlock found by a test, then designed out.** The concurrency integration test showed that parallel inserts
  could deadlock on the two constraints (Postgres `40P01`). Booking create and extend now lock the user row, then the
  spot row (`SELECT ... FOR UPDATE`, always in that order) before checking overlaps. The loser of a race gets a clean
  409. Sensor batches lock their spots in id order for the same reason.
- **Sensor state is separate from manager state.** `occupied` is sensor-driven, while `enabled` and
  `maintenanceRequired` belong to managers. A reading can never re-open a spot a manager closed. A spot is bookable
  only when `lot.enabled && spot.enabled && !spot.maintenanceRequired`.
- **Notifications only for committed changes.** Domain events are handled by `@TransactionalEventListener(AFTER_COMMIT)`
  listeners running in their own transaction. A rolled-back booking never notifies anyone, and a failing listener
  can't undo a committed booking.
- **Round-up billing.** Billable hours are the duration rounded up to the next whole hour. The legacy version
  truncated, which made sub-hour stays free. Money is `BigDecimal` with scale 2 everywhere.
- **Testable time.** Every time-dependent component takes an injected `java.time.Clock`; nothing calls `now()` on
  its own. Integration tests swap in a mutable clock to move through booking lifecycles, overstays and
  auto-checkouts deterministically.
- **Constant-time sensor key check.** The `X-Sensor-Key` filter compares SHA-256 digests with
  `MessageDigest.isEqual`. That makes the comparison constant-time and hides the key's length.
- **Generated, type-checked API contract.** The frontend's TypeScript API types are generated from the backend's
  OpenAPI document (`openapi-typescript`). A DTO change surfaces as a compile error in the UI instead of a runtime bug.
- **Production safety.** With the `prod` profile, startup fails fast with a readable report if:
  - the JWT secret, sensor key or super-manager password still has its development default;
  - the JWT secret is shorter than 32 bytes;
  - the `demo` profile is also active.
- **Containers.** The backend image is a layered Spring Boot jar on a JRE, running as a non-root user with a health
  check and heap sized from the container limit. The frontend is unprivileged nginx with a strict Content-Security-Policy,
  immutable caching for hashed assets, and gzip.
- **Realistic, deterministic demo data.** A seeded planner generates residents, commuters, faculty, staff and
  visitors with weekday, weekend and holiday patterns. It follows every booking and payment rule the API enforces, and
  the result is batch-inserted in under a second.

## Deployment

The two images and a managed PostgreSQL 16 are all you need. Below is an example for [Render](https://render.com);
other platforms, such as Fly.io or Railway, follow the same shape. These are instructions only: nothing in this
repository deploys automatically.

1. **Database.** Create a PostgreSQL 16 instance and note its internal host, port, database, user and password.
2. **Backend.** Create a *Private Service* from this repository:
   - Runtime **Docker**, root directory `backend`.
   - Set `PORT=8080` (Render routes to that port).
   - Set the environment variables:

   | Variable | Value |
   |---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` |
   | `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | from step 1, or set `SPRING_DATASOURCE_URL` (e.g. with `?sslmode=require`) plus `DB_USER` and `DB_PASSWORD` |
   | `APP_JWT_SECRET` | at least 32 random bytes, e.g. `openssl rand -base64 48` |
   | `APP_SENSORS_API_KEY` | a long random string, shared with your sensors |
   | `APP_SUPER_MANAGER_EMAIL`, `APP_SUPER_MANAGER_PASSWORD` | the first admin account (created once) |
   | `APP_CORS_ALLOWED_ORIGINS` | the public web URL (only matters for cross-origin clients) |

   The `prod` profile refuses to start if any secret is left at its development default.
3. **Frontend.** Create a *Web Service*:
   - Runtime **Docker**, root directory `frontend`.
   - Set `PORT=8080` and `BACKEND_URL=http://<backend-internal-host>:8080`.
   - For a non-demo deployment, set the Docker build argument `VITE_SHOW_DEMO_ACCOUNTS=false`.
4. **Check the deployment.**
   - Open the web URL and sign in as the super manager.
   - Create a manager.
   - Confirm that `/swagger-ui.html` loads.
   - The backend's health endpoint is `/actuator/health`, and the image's `HEALTHCHECK` uses it.

**Public demo instance.** To host a demo with seeded data instead, run the backend with `SPRING_PROFILES_ACTIVE=demo`
and still override every secret. `prod` and `demo` can't be combined, and only `prod` enforces the secrets. Keep a
single backend instance: the scheduled jobs assume one replica.

## Project history

CampusPark started as a York University **EECS 3311 (Software Design)** course project. That was a plain Java codebase
(no UI or HTTP API) with CSV-file storage, built to practise design patterns (Builder, Iterator, Observer, Factory, Facade) with
Randoop and PIT tests. It was later rebuilt end to end with a new architecture, database, security model, UI,
tests and delivery pipeline. The domain ideas carried over, and the legacy bugs were designed out. The original code
is preserved at the
[`legacy-coursework`](https://github.com/amir01mn/campus-lot-manager/tree/legacy-coursework) tag, and the rebuild
plan and decision log are in [`docs/PLAN.md`](docs/PLAN.md).

## Roadmap

- A real payment gateway (e.g. Stripe) behind the existing `PaymentGateway` interface, with webhooks for refunds.
- Real email delivery behind `EmailSender` (SMTP or a transactional email API).
- Push updates over WebSockets or Server-Sent Events instead of polling the spot map and notifications.
- ShedLock (or similar) so the scheduled jobs can run with more than one backend replica.
- Refresh tokens in an httpOnly cookie instead of a tab-scoped access token.
- Metrics and tracing (Micrometer, Prometheus, OpenTelemetry) and structured JSON logs.
- French localisation (Glendon is a bilingual campus).

## License

[MIT](LICENSE) © 2026 amir01mn
