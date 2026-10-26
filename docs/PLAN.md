# Campus Parking — Rebuild Plan & Spec

This is the single source of truth for the rebuild of the original EECS 3311 coursework
(tag `legacy-coursework`) into a production-style full-stack application. Every phase
must follow the rules here. If something here is ambiguous, pick the simplest reasonable
option and record the decision in the "Decision log" at the bottom of this file.

## Goals

- A runnable, deployable app: `docker compose up` starts Postgres + API + web UI.
- Domain behaviour faithful to the original idea (tiered pricing, deposits, sensors,
  manager controls), with all known legacy bugs designed out.
- Professional engineering: layered architecture, migrations, security, real tests, CI,
  API docs, a strong README.

## Stack

| Concern | Choice |
|---|---|
| Language | Java 17 (installed JDK is Temurin 17) |
| Backend | Spring Boot (latest stable 3.5.x; only use 4.x if every needed library supports it cleanly), Maven Wrapper |
| Persistence | PostgreSQL 16, Spring Data JPA, Flyway |
| Security | Spring Security, stateless JWT via `spring-boot-starter-oauth2-resource-server` + `NimbusJwtEncoder` (HS256, secret from config), BCrypt |
| API docs | springdoc-openapi (Swagger UI at `/swagger-ui.html`) |
| Validation | Jakarta Bean Validation; errors as RFC 7807 `ProblemDetail` |
| Backend tests | JUnit 5, AssertJ, Mockito, Spring Boot Test, Testcontainers (Postgres) |
| Frontend | React + TypeScript + Vite, React Router, TanStack Query, Tailwind CSS, Vitest + Testing Library |
| Packaging | Dockerfiles for backend and frontend (nginx), root `docker-compose.yml` |
| CI | GitHub Actions: backend build+tests, frontend lint+typecheck+test+build |

Base package: `io.github.amir01mn.parking`. Maven groupId `io.github.amir01mn`, artifactId `campus-parking`.

## Repository layout (target)

```
backend/            Spring Boot app (mvnw, pom.xml, src/main/java, src/test/java)
frontend/           Vite React app
docs/               PLAN.md, architecture notes, screenshots
docker-compose.yml  postgres + backend + frontend
.github/workflows/  ci.yml
README.md
LICENSE             MIT
```

The legacy `src/`, `test/`, `pom.xml`, `classlist*.txt`, `pit-randoop-config.xml`,
`run-pit-*.sh` are removed in Phase 1 (history preserved by the `legacy-coursework` tag).

Backend package structure (feature-oriented):

```
io.github.amir01mn.parking
  config/         security, openapi, clock, properties
  common/         error handling, base types, Money helpers
  user/           User entity, roles, directory lookup, registration
  auth/           login, JWT issuing
  lot/            ParkingLot, ParkingSpot, manager operations
  booking/        Booking, pricing, booking service
  payment/        Payment records, PaymentGateway (simulated)
  sensor/         sensor ingestion, simulator
  notification/   notifications + domain event listeners
  analytics/      manager dashboard stats
```

Inject a `java.time.Clock` bean everywhere time matters (never call `now()` without it),
so tests can control time. Campus time zone: `America/Toronto`. Store timestamps as
`timestamptz`, use `Instant`/`OffsetDateTime` in Java, ISO-8601 in JSON.
Money: `BigDecimal`, scale 2, currency CAD, `numeric(10,2)` in the DB.

## Domain rules

### Users and roles
- `UserType` (pricing tier): `STUDENT`, `FACULTY`, `STAFF`, `VISITOR`.
- `Role` (authorization): `USER`, `MANAGER`, `SUPER_MANAGER`.
- Registration (public): first name, last name, email, password. Email unique,
  case-insensitive. The user type is **not** chosen by the user: it is looked up in the
  `university_directory` table (email → type), seeded from the legacy YU database idea.
  Not found → `VISITOR`. Registered users get role `USER`.
- Password policy: ≥ 8 chars with upper, lower, digit, and special character. Stored with BCrypt.
- A failed login never changes anything (the legacy "reset password on failed login" bug must not exist).
- Super manager is bootstrapped at startup from config (`app.bootstrap.super-manager.email/password`,
  env-overridable); no hardcoded credentials in code. Only the super manager can create managers;
  `POST /api/admin/managers` returns a generated secure password (SecureRandom) once.

### Hourly rates (CAD)
| Type | Rate/hour |
|---|---|
| STUDENT | 5.00 |
| FACULTY | 8.00 |
| STAFF | 10.00 |
| VISITOR | 15.00 |

Rates live in configuration (`app.pricing.rates.*`), not hardcoded in logic.

### Lots and spots
- `ParkingLot`: id, name, location, enabled (bool), createdAt.
- `ParkingSpot`: id, lot, label (e.g. `A-001`), sensorId (unique), enabled (manager control),
  maintenanceRequired (bool), occupied (bool, sensor-driven), lastSensorUpdate.
- Manager creates a lot with a capacity (1–500, default 100); spots are generated with labels and sensor ids.
- Manager state and sensor state are **separate fields**: a sensor reading never changes `enabled`
  or `maintenanceRequired`. Disabling a lot does not rewrite its spots' flags; a spot is bookable only if
  `lot.enabled && spot.enabled && !spot.maintenanceRequired`.

### Bookings
- Fields: id (UUID), reference code (e.g. `CP-7F3K2Q`, human friendly), user, spot, licence plate,
  startTime, endTime, status, hourlyRate (snapshot at booking time), depositAmount, totalAmount, createdAt.
- Status: `CONFIRMED` → `COMPLETED`, or `CONFIRMED` → `CANCELLED`.
- Create: start ≥ now (allow 5 min grace), end > start, duration 30 min – 24 h, plate matches
  `^[A-Z0-9 -]{2,10}$` after uppercasing. The spot must be bookable. No overlapping `CONFIRMED`
  booking on the same spot (half-open intervals `[start, end)`), and the user may not hold two
  overlapping `CONFIRMED` bookings.
  Double booking is also prevented at the DB level with a Postgres exclusion constraint
  (`btree_gist`, `EXCLUDE USING gist (spot_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&) WHERE (status = 'CONFIRMED')`);
  map constraint violations to HTTP 409.
- Pricing: billable hours = duration rounded **up** to the next whole hour. total = rate × billable hours.
  Deposit = one hour at the user's rate, charged when the booking is created.
- Extend: only `CONFIRMED` bookings that haven't ended; new end > current end; same conflict
  rules; total recalculated from the full new duration.
- Cancel: owner (or a manager) may cancel a `CONFIRMED` booking before its end.
  If cancelled ≥ 1 hour before start → deposit refunded (REFUND payment). Otherwise the deposit is kept.
- Checkout: owner checks out after start, or a scheduled job auto-completes bookings whose end has passed.
  Final charge = total − deposit (if > 0) recorded as a FINAL payment. Status → `COMPLETED`.
- Receipt: `GET /api/bookings/{id}/receipt` returns booking summary + all payments.

### Payments
- `Payment`: id, booking, type (`DEPOSIT`, `FINAL`, `REFUND`), amount, method
  (`CREDIT_CARD`, `DEBIT_CARD`, `PAYPAL`), status (`SUCCEEDED`, `FAILED`), createdAt.
- Payment method is chosen when booking. All charges go through a `PaymentGateway` interface;
  the only implementation is `SimulatedPaymentGateway` (always succeeds, logs). Structured so a real
  gateway (e.g. Stripe) could be plugged in.

### Sensors
- `POST /api/sensors/readings` accepts a batch of `{sensorId, occupied, timestamp}`. Authenticated by
  an `X-Sensor-Key` header matching `app.sensors.api-key` (not JWT). Readings older than the spot's
  `lastSensorUpdate` are ignored.
- Alerts raised as domain events → notifications to managers:
  - **Overstay**: spot occupied while its booking's end has passed (checked by the scheduled job).
  - **Unauthorized parking**: spot reports occupied with no `CONFIRMED` booking covering now.
  - **Maintenance**: a manager flags a spot for maintenance.
- A `SensorSimulator` (enabled only with Spring profile `demo`) periodically produces plausible
  readings so the dashboard looks alive.

### Notifications
- Observer pattern via Spring `ApplicationEvent`s (`BookingConfirmed`, `BookingCancelled`,
  `BookingExtended`, `BookingCompleted`, `OverstayDetected`, `UnauthorizedParkingDetected`,
  `MaintenanceFlagged`). Listeners persist `Notification` rows (recipient user, type, message,
  read flag, createdAt). No real email; an `EmailSender` interface with a logging implementation.

### Analytics (manager dashboard)
- `GET /api/manager/stats?from=&to=`: total spots, currently occupied, occupancy %, bookings count,
  revenue (sum of succeeded DEPOSIT + FINAL − REFUND) in range, bookings per day, revenue by user type,
  per-lot utilisation.

## REST API contract

All under `/api`. JSON. Errors are `ProblemDetail`. Paginated lists use `?page=&size=` and return
Spring's page shape `{content, totalElements, totalPages, number, size}`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | /auth/register | public | register → 201 user profile |
| POST | /auth/login | public | `{email,password}` → `{accessToken, expiresIn, user}` |
| GET | /users/me | USER+ | current profile (incl. type, role, hourly rate) |
| PUT | /users/me/password | USER+ | `{currentPassword,newPassword}` |
| GET | /lots | public | list lots with counts (total, bookable, occupied) |
| GET | /lots/{id} | public | lot detail |
| GET | /lots/{id}/spots | public | spots with live state |
| GET | /lots/{id}/availability?start=&end= | public | spots free for the window |
| POST | /bookings | USER+ | `{spotId, plate, start, end, paymentMethod}` → 201 |
| GET | /bookings/me | USER+ | my bookings (filter `status`) paginated |
| GET | /bookings/{id} | owner/MANAGER | detail |
| POST | /bookings/{id}/extend | owner | `{newEnd}` |
| POST | /bookings/{id}/cancel | owner/MANAGER | |
| POST | /bookings/{id}/checkout | owner | |
| GET | /bookings/{id}/receipt | owner/MANAGER | |
| GET | /pricing/quote?start=&end= | USER+ | price preview for current user |
| GET | /notifications | USER+ | my notifications paginated |
| POST | /notifications/{id}/read | USER+ | mark read |
| POST | /manager/lots | MANAGER+ | `{name, location, capacity}` |
| PATCH | /manager/lots/{id} | MANAGER+ | `{enabled?, name?, location?}` |
| PATCH | /manager/spots/{id} | MANAGER+ | `{enabled?, maintenanceRequired?}` |
| GET | /manager/bookings | MANAGER+ | all bookings, filters `status, lotId, from, to` |
| GET | /manager/stats | MANAGER+ | analytics above |
| POST | /admin/managers | SUPER_MANAGER | `{firstName,lastName,email}` → manager + one-time password |
| GET | /admin/managers | SUPER_MANAGER | list managers |
| POST | /sensors/readings | X-Sensor-Key | batch readings |

CORS: allow the frontend origin from config (`app.cors.allowed-origins`, default `http://localhost:5173`).

## Seed data (Flyway, dev/demo)
- `university_directory`: ~20 rows across STUDENT/FACULTY/STAFF with `@yorku.ca` / `@my.yorku.ca` emails.
- 3 lots (e.g. "Lot A — Keele Campus North", "Lot B — Stong Pond", "Lot C — Glendon"), 40/60/30 spots.
- Demo users (repeatable migration or `demo` profile seeder): one per type plus a manager, all with
  documented demo passwords listed in the README. The super manager comes from bootstrap config.

## Phases

Status: all five phases are complete (October 2026).

1. ✅ **Foundation**: remove legacy code, scaffold `backend/` (wrapper, pom, config, profiles
   `default`/`test`/`demo`), docker-compose with Postgres, Flyway schema + seed, JPA entities +
   repositories, pricing service (pure, unit tested), global error handling, health endpoint,
   Testcontainers base test. Build green.
2. ✅ **Core API**: security + JWT, auth/register/login/me, directory lookup, lots/spots read
   endpoints + availability, bookings (create/extend/cancel/checkout/receipt/quote), payments,
   OpenAPI. Unit + integration tests.
3. ✅ **Operations API**: manager and admin endpoints, sensors ingestion + simulator, scheduled
   jobs (auto-checkout, overstay/unauthorized detection), notifications via events, analytics.
   Tests.
4. ✅ **Frontend**: React app: auth pages, lot browser with live spot grid, booking flow with price
   quote, my bookings (extend/cancel/checkout/receipt), notifications, manager dashboard (stats
   charts, lot/spot controls, bookings table), admin managers page. Tests for key components.
5. ✅ **Ship**: Dockerfiles, full `docker-compose.yml`, GitHub Actions CI, README rewrite
   (overview, screenshots, architecture diagram, run instructions, demo accounts, API docs link,
   design decisions), LICENSE, final consistency pass.

## Conventions for all phases
- Do not commit or push; the project manager reviews and commits each phase.
- Keep the build green: `cd backend && ./mvnw -q verify` (Docker is available for Testcontainers).
- Constructor injection, DTOs (Java records) at the API boundary, never expose entities.
- No `System.out`; use SLF4J. No swallowed exceptions.
- Update the Decision log for any non-obvious choice.

## Decision log
- Legacy CSV storage replaced by PostgreSQL; legacy code retired rather than patched.
- Sensor state and manager state are separate fields so sensors can't undo manager actions.
- Billable hours round up (legacy truncated, making sub-hour bookings free).
- Phase 1: Spring Boot 3.5.16 (latest 3.5.x on Maven Central; start.spring.io now only offers 4.x) with
  springdoc-openapi 2.10.0 (built against Boot 3.5.16). Maven Wrapper 3.3.4 (only-script) pinned to Maven 3.9.11.
- IDs: `users`, `bookings`, `payments`, `notifications` use UUIDs (generated by Hibernate); `parking_lots` and
  `parking_spots` use bigint identity for short, readable URLs. Entity `equals` compares non-null ids;
  `hashCode` is a per-class constant (stable across persist and Hibernate proxies).
- Emails are stored lower-cased (`CHECK (email = lower(email))`) with a plain UNIQUE constraint, giving
  case-insensitive uniqueness; callers normalise with `User.normalizeEmail` before lookups.
- `bookings` has a second exclusion constraint `ex_bookings_user_no_overlap` (same shape, on `user_id`) as
  defence in depth for the one-booking-at-a-time rule. Both map to 409 via SQLSTATE `23P01`.
- `bookings` also stores `payment_method` (needed for the FINAL charge), `cancelled_at`, `completed_at`.
  `payments.provider_reference` holds the gateway transaction id. `notifications` has optional
  `booking_id`/`spot_id` links. `@Version` optimistic locking on users, lots, spots, bookings → 409.
- Seeded spots: labels `A-001`… per lot prefix, sensor ids `SNS-<prefix>-NNN`. Phase 3 must choose a prefix
  scheme for manager-created lots that keeps `sensor_id` globally unique.
- Problem `type` URIs are `urn:campus-parking:problem:<slug>`; 422 responses carry a machine-readable `code`.
- `test` profile config lives in `src/test/resources/application-test.yml`; the Testcontainers Postgres is a
  JVM-wide singleton shared by all integration tests (no `withReuse`, so nothing outlives the build).
- Phase 2: JWT claims are `sub` = user id, `role` = role name (mapped to `ROLE_*`), plus `iss` and `email`. Tokens are
  validated against the injected `Clock` (30 s skew). Roles are hierarchical via a `RoleHierarchy` bean. Public:
  `POST /api/auth/{register,login}`, `GET /api/lots/**`, health, api-docs, swagger. `/api/sensors/**` is permitted at
  the filter chain (Phase 3 adds the X-Sensor-Key filter); `/api/manager/**` needs MANAGER, `/api/admin/**`
  SUPER_MANAGER, every other `/api/**` USER, anything else is denied. A bad bearer token on a public endpoint is still 401.
- Phase 2: 401/403 from the filter chain are ProblemDetail JSON (`unauthorized` / `forbidden`), and a 401 carries
  `WWW-Authenticate: Bearer`. Login failures answer "Invalid email or password." whether or not the e-mail exists, with a
  dummy BCrypt check for unknown e-mails. Login runs in a read-only transaction and never writes.
- Phase 2: the password policy also caps passwords at 72 UTF-8 bytes (BCrypt's limit). Whitespace doesn't count as a special
  character. A password change needs the current password and rejects an unchanged one (422).
- Phase 2: booking visibility. Non-owners who aren't managers get **404** for any booking endpoint, so booking ids don't
  leak. Managers (and super managers) can view, cancel and get receipts for any booking, but extend and checkout are
  owner-only and a manager gets 403 for them.
- Phase 2: extend applies the same rules as create: the spot must still be bookable, the total duration stays at most
  24 h, and spot/user overlaps are checked on `[currentEnd, newEnd)`. The hourly-rate snapshot is kept and the deposit
  is unchanged. Checkout charges the booked total (no pro-rating for leaving early).
- Phase 2: concurrency. Concurrent inserts can deadlock on the two exclusion constraints (Postgres `40P01`, seen in tests).
  So create and extend lock the user row and then the spot row (`SELECT ... FOR UPDATE`, always in that order) before
  the overlap checks. The losing request gets the friendly 409. Exclusion violations and lock failures that still occur map
  to 409.
- Phase 2: reference codes are `CP-` plus 6 SecureRandom characters from `23456789ABCDEFGHJKMNPQRSTUVWXYZ` (no
  look-alikes). The code is pre-checked with `existsByReferenceCode`, retried up to 10 times, and the unique constraint
  is the backstop.
- Phase 2: payments go through `PaymentService.record`, which joins the booking transaction (MANDATORY). A declined
  gateway result is 422 `PAYMENT_DECLINED` and rolls the booking change back, so the FAILED row is rolled back too.
  Phase 3+ can move it to `REQUIRES_NEW` if failed attempts must be kept.
- Phase 2: `BookingCompletionService.complete(Booking)` (in the caller's transaction) and `completeIfConfirmed(UUID)` (own
  transaction, idempotent) are the shared completion path for checkout and Phase 3's auto-checkout. Booking events
  (`booking.event.*` records) are published synchronously inside the transaction. Listeners should use
  `@TransactionalEventListener`.
- Phase 2: paginated responses use our own `PageResponse` record, because Spring Data's `PageImpl` JSON is not a stable API.
  The quote endpoint validates only the window's shape (end > start, 30 min to 24 h), not that it's in the future.
- Phase 2: the super manager bootstrap creates the account once (type STAFF) and never modifies an existing account. A blank
  e-mail or password logs a warning instead of failing startup (`BootstrapProperties` no longer requires them).
- Phase 2: demo data (profile `demo`, `DemoDataSeeder`). The accounts are student.demo@my.yorku.ca, faculty.demo@yorku.ca,
  staff.demo@yorku.ca, visitor.demo@example.com and manager.demo@yorku.ca (MANAGER, type STAFF). They share the
  password `app.demo.password` (default `Demo#2026`, env `APP_DEMO_PASSWORD`). Existing accounts are left alone. Bookings
  are seeded only while no demo account has any: per customer, 4 past COMPLETED (DEPOSIT+FINAL), 1 CANCELLED with
  REFUND and 1 CONFIRMED tomorrow (DEPOSIT). Seeded payments use `SEED-...` provider references and bypass the gateway.
- Phase 2 tests: the integration context replaces the clock with a `@Primary` `MutableClock`. API tests clean bookings,
  payments, notifications and non-super-manager users before and after each test. The test JWT expiry is PT12H, so
  tests that move the clock forward keep valid tokens.
- Phase 3: notifications. `NotificationEventListener` handles every booking and alert event with
  `@TransactionalEventListener(AFTER_COMMIT)` plus `@Transactional(REQUIRES_NEW)`, so a rolled-back transaction never
  notifies. A failing listener can't undo the committed change; Spring logs the error. Booking events notify the owner
  in-app and through `EmailSender` (only implementation: `LoggingEmailSender`). Alert events notify every MANAGER and
  SUPER_MANAGER in-app only. Alert event records live in `sensor.event` (`OverstayDetected`, `UnauthorizedParkingDetected`)
  and `lot.event` (`MaintenanceFlagged`), and carry labels and names so listeners don't reload them. Another user's
  notification id is 404. `read-all` answers `{updated}` and `unread-count` answers `{count}`.
- Phase 3: sensors. `SensorKeyAuthenticationFilter` runs only inside the security chain (it is not a bean, so it isn't a
  global servlet filter). It compares SHA-256 digests with `MessageDigest.isEqual`, so the comparison is constant time and
  hides the key length. A missing or wrong key gets a 401 ProblemDetail; a valid key grants `ROLE_SENSOR`, and
  `/api/sensors/**` now requires it instead of `permitAll`. The body is `{readings: [...]}` (1–500). Each batch locks its
  spots `FOR UPDATE` in id order, so it serialises with bookings and manager PATCHes (no optimistic-lock 409s) without
  deadlocking. Readings are applied in timestamp order. Readings more than `app.sensors.max-clock-skew` (PT5M) in the
  future are not applied, because they would make every later reading look stale. They are counted in an extra response
  field, `ignoredFuture`. `unknownSensors` is a sorted, de-duplicated list.
- Phase 3: unauthorized parking is detected only on an unoccupied -> occupied transition, when no CONFIRMED booking covers
  the server clock's now. Continued occupied readings are not transitions, so one continuous occupancy alerts once with no
  extra state. V3 adds `parking_spots.occupied_since` (start of the current occupancy; `SpotResponse.occupiedSince`) and
  `bookings.overstay_alerted_at`.
- Phase 3: overstay. A non-cancelled booking (auto-checkout normally completes it first) whose end is more than
  `app.jobs.overstay.grace` (PT10M) in the past but within `lookback` (PT24H) is an overstay when all of these hold:
  - its spot is still occupied by an occupancy that began before the booking ended (`occupied_since < end_time`);
  - no CONFIRMED booking covers now;
  - no later non-cancelled booking on that spot has started.
  The job sets `overstay_alerted_at` in the same transaction as the event, so each booking alerts at most once.
- Phase 3: scheduling. `SchedulingConfig` (`@EnableScheduling`) is active unless `app.jobs.enabled=false`, which the test
  profile sets; tests call the job beans' `run()` directly. Intervals are `app.jobs.auto-checkout.interval` and
  `app.jobs.overstay.interval` (PT1M). The scheduler pool size is 3. Auto-checkout runs `completeIfConfirmed` once per id,
  each in its own transaction, and logs a failure without stopping the run. Jobs assume one API instance; multiple
  replicas would need ShedLock or similar.
- Phase 3: the sensor simulator (`demo` profile and `app.sensors.simulator.enabled`) samples `sample-fraction` (0.25) of
  spots every `interval` (PT30S). A spot covered by a booking reports occupied with p = 0.85, any other spot with
  p = 0.003 (about one unauthorized alert every few minutes). It calls `SensorService.ingest`, the same service the HTTP
  endpoint uses. The generation logic is the pure class `SimulatedReadingGenerator`.
- Phase 3: spots in manager-created lots are labelled `L<lotId>-NNN`, with sensor ids `SNS-L<lotId>-NNN`. The lot id
  guarantees uniqueness and can't collide with the seeded `SNS-<A|B|C>-NNN` ids. Seeded lots keep their labels. Duplicate
  lot names (also on rename) are 409.
- Phase 3: disabling a lot or spot, or flagging maintenance, never cancels existing CONFIRMED bookings. They stay valid,
  but they can't be extended while the spot is unbookable. The PATCH responses are `{lot|spot, affectedUpcomingBookings}`,
  where the count covers not-yet-ended CONFIRMED bookings while the target is unbookable (0 otherwise). `MaintenanceFlagged`
  fires only on a false -> true change.
- Phase 3: `GET /api/manager/bookings` filters `from`/`to` by overlap with `[from, to)`. `q` is a case-insensitive
  substring of the reference code or plate, with LIKE wildcards escaped. Results are newest start first, shaped
  `{booking: BookingResponse, customer: {id, email, firstName, lastName, userType}}`.
- Phase 3: stats definitions for `[from, to)` (default: the 30 days before now; at most 366 days):
  - `bookingsCount` and `bookingsByStatus` count bookings that start in the range, whatever their status.
  - `bookingsPerDay` counts the same bookings by their start date in America/Toronto, zero-filled.
  - `revenue` is succeeded DEPOSIT + FINAL − REFUND payments created in the range; `revenueByUserType` splits it by the
    payer's tier (all four tiers are present).
  - Per-lot utilisation = booked hours of non-cancelled bookings, clipped to the range, divided by (current spot count ×
    range hours).
  - Percentages have 1 decimal place.
  - Everything is computed by aggregate SQL through `NamedParameterJdbcTemplate`.
- Phase 3: `POST /api/admin/managers` generates a 16-character SecureRandom password with all four character classes and
  no look-alike characters. It is returned once as `temporaryPassword` and never logged. The tier comes from the
  directory, and unlisted e-mails get STAFF. `GET /api/admin/managers` is paginated (`PageResponse`) and sorted by name.
- Phase 3: the demo seeder also adds notifications (each customer's latest completed, cancelled and confirmed booking;
  two sample alerts for the demo manager) while no demo account has any. This runs independently of the booking seeding,
  so existing Phase 2 demo databases get them too.
- Phase 4: frontend stack (exact versions pinned in `package-lock.json`): Vite 8.3, React 19.3, React Router 8.5 (library
  mode, `createBrowserRouter`), TanStack Query 5.104, Tailwind CSS 4.3 (CSS-first `@theme` tokens, class-based dark mode),
  Recharts 3.10, lucide-react icons, self-hosted Inter (`@fontsource-variable/inter`). Tooling: TypeScript 5.9.3 (strict,
  `noUncheckedIndexedAccess`), ESLint 10 + typescript-eslint 8 `strictTypeChecked`, Vitest 5 + Testing Library + MSW 3
  (jsdom), Playwright 1.64. TypeScript stays on 5.9 because typescript-eslint supports `<6.1` and openapi-typescript
  declares `^5`. `eslint-plugin-jsx-a11y` is left out because it doesn't support ESLint 10 yet; accessibility is covered by
  role-based Testing Library queries instead.
- Phase 4: API types are generated from `/v3/api-docs` with openapi-typescript (`npm run gen:api`, backend running) into
  `src/api/schema.gen.ts`, using `--properties-required-by-default`. springdoc doesn't mark Java record components as
  required, and without the flag every field would be optional. `src/api/types.ts` widens the few genuinely nullable
  fields (`cancelledAt`, `completedAt`, `occupiedSince`, `lastSensorUpdate`, notification links, `providerReference`)
  to `| null`, and makes PATCH bodies `Partial`. Regenerating after a DTO change then surfaces drift as type errors. All
  calls go through one `fetch` wrapper (`src/api/client.ts`) that throws `ApiError` carrying the ProblemDetail;
  `src/api/problem.ts` turns it into user copy (422 `code` → message, 400 `errors` → per-field messages).
- Phase 4: the JWT lives in memory and is mirrored to `sessionStorage` (per tab, cleared when the tab closes). It is
  dropped on sign-out, on its expiry (timer from `expiresIn`), and on any 401 from a request that carried it. Expiry or a
  401 sends the user to `/login?reason=expired&next=…`, and only same-app `next` paths are honoured. Sign-out lands on
  `/login?reason=signed-out` with no `next`. Route guards mirror the backend's role hierarchy.
- Phase 4: all times are entered and shown in campus time (America/Toronto) whatever the browser zone; conversion uses
  `Intl` (no date library) and is DST-tested. A booking window is date + start + end in half-hour steps; an end at or
  before the start means the next day. Money is formatted as CAD with `Intl.NumberFormat('en-CA')`.
- Phase 4: spot grid states are, in priority order: unavailable (manager-disabled or lot closed), maintenance, booked for
  the chosen window (bookable but missing from `/availability`), occupied now (sensor sees a car but the spot is free for
  the window, so it stays bookable), and free. Each state has an icon and a pattern (dashed border or stripes) as well as
  a colour, and a legend with counts. The grid is an ARIA radio group with roving tabindex and arrow-key navigation. Spots
  and availability poll every 15 s, lots and stats every 30 s, and the unread-notification count every 30 s.
- Phase 4: My bookings tabs are derived client-side: CONFIRMED bookings (one request, up to 100) split by start into
  Upcoming and Active, plus paginated COMPLETED (Past) and CANCELLED. A CONFIRMED booking that has ended waits for
  auto-checkout under Active. The cancel dialog computes the refund outcome with the backend's rule (refund iff
  now ≤ start − 1 h) before confirming. The manager dashboard range picker works in campus days and is sent as a
  half-open `[from, to)` instant range.
- Phase 4: manager and admin pages are lazy-loaded (`React.lazy`), so Recharts (~100 kB gzip) isn't in the driver bundle.
  The dev and preview servers proxy `/api`, `/swagger-ui` and `/v3/api-docs` to `VITE_API_PROXY_TARGET` (default
  `http://localhost:8080`) and strip the `Origin` header, because the browser call is same-origin and the backend's CORS
  allow-list only matters for real cross-origin callers. Demo sign-in buttons are hidden with
  `VITE_SHOW_DEMO_ACCOUNTS=false` at build time.
- Phase 4: `npm run e2e` (Playwright, not part of `npm test`) needs the demo backend running; it starts or reuses
  `npm run dev`. `npm run screenshots` regenerates `docs/screenshots/*.png`. It first makes the demo look busy using only
  the public API: about 65 extra drivers (directory e-mails plus `@example.com` visitors), bookings for now and the next
  days, sensor readings with the dev sensor key, and a few spots taken out of service by the demo manager.
- Phase 5: production safety. The `prod` profile check is an `EnvironmentPostProcessor` (`ProductionSafetyCheck`,
  registered in `META-INF/spring.factories`), so it runs before any bean or database connection exists. It refuses to
  start while the JWT secret, sensor key or super-manager password still equals its `application.yml` dev default, when
  the JWT secret is under 32 UTF-8 bytes, or when `demo` is also active. A `FailureAnalyzer` prints the problems as a
  normal "APPLICATION FAILED TO START" report. A test loads the real `application.yml` so the constants can't drift.
  `prod` also sets `server.forward-headers-strategy=framework` (behind nginx or a platform proxy) and INFO logging.
- Phase 5: demo history. `DemoHistorySeeder` (demo profile, runs after `DemoDataSeeder`) writes what the pure
  `DemoHistoryPlanner` plans:
  - drivers in five personas (residence students parking overnight, commuting students, faculty, staff, visitors);
  - 60 days of history and 7 days ahead, shaped by weekdays, Fridays, weekends and Ontario holidays;
  - favourite spots and home lots per driver.
  The planner follows the API's rules: no overlapping non-cancelled bookings per spot or driver, durations from 30 min
  to 24 h, PricingService amounts, a refund exactly when cancelled at least 1 h before the start, nothing dated after
  now, and confirmed bookings only on bookable spots. Fixed seeds make it deterministic relative to today.
  The population is 142 drivers rather than the ~40 first suggested. Utilisation is booked hours over 24 h × 130
  spots, so 40 drivers can reach only about 10% without breaking the one-booking-at-a-time rule; 142 gives 15-26% per
  lot over 30 days. Student and faculty/staff drivers get directory rows inserted by the seeder (not Flyway), so their
  tiers resolve as they would on registration. All drivers share the demo password (one BCrypt hash).
  Idempotency key: whether any planned driver e-mail already exists (the plan never depends on database contents).
  Rows are written in one transaction with JDBC batch inserts (about 5k bookings and 10k payments in under a second),
  with bookings ordered by spot id. That is the lock order sensor ingestion uses, so the simulator, which starts
  5 s after boot, can't deadlock with seeding. `e2e/support/populateDemo.ts` now only posts sensor readings for spots
  booked right now, adds one stray car, and flags A-012 (maintenance) and B-059 (disabled).
- Phase 5: `SensorSimulator` uses `SplittableRandom`. `RandomGenerator.getDefault()` needs the `jdk.random` module,
  which the slim Temurin JRE image doesn't include, so the demo backend crashed on startup in Docker.
- Phase 5: containers.
  - Backend: built with the Maven wrapper (`dependency:go-offline` layer, tests skipped because CI runs them),
    extracted with `-Djarmode=tools extract --layers --launcher`, and run on `eclipse-temurin:17-jre-alpine` as UID
    10001 with `JAVA_OPTS` (`MaxRAMPercentage=75`). The `HEALTHCHECK` is `wget` on `/actuator/health`. The image
    defaults to the `prod` profile; Compose overrides it with `demo`.
  - Frontend: `nginxinc/nginx-unprivileged` (port 8080). `nginx.conf` is an envsubst template, so `BACKEND_URL` can
    point at another host on a platform.
  - Security headers come from a snippet included per static location, because a location's own `add_header` would
    drop inherited ones. Proxied API responses keep Spring Security's headers.
  - The CSP is `script-src 'self'`. For that, the pre-paint theme script moved from inline in `index.html` to
    `public/theme-init.js`.
  - The proxy sends `Host: $http_host` (with the port), so springdoc's server URL is the public origin, and it
    blanks `Origin`. It honours an upstream `X-Forwarded-Proto`.
- Phase 5: Compose project name `campus-lot-manager`. Postgres binds to `127.0.0.1` only, and the backend uses
  `expose`, not `ports`. Every variable has a demo-safe default (so no `.env` is needed), mirrored in `.env.example`.
  `docker compose up -d postgres` still runs just the database.
- Phase 5: CI has four jobs: backend (`./mvnw -B verify`), frontend (lint, typecheck, test, build), e2e (Compose
  `--wait`, Playwright Chromium against :3000, logs and report on failure, `down -v` always) and docker (Buildx with the
  GitHub Actions cache). `backend/mvnw` is now tracked as executable (100755). Dependabot updates maven, npm (minor and
  patch grouped), github-actions and docker weekly.
- Phase 5: the dashboard's bookings-per-day Y axis no longer uses a negative left margin, which clipped three-digit
  tick labels once the demo had realistic volumes. The lot-detail spot badge is `shrink-0` with the text column
  `min-w-0`, so long labels such as `L12-104` fit.
