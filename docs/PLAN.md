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

1. **Foundation**: remove legacy code, scaffold `backend/` (wrapper, pom, config, profiles
   `default`/`test`/`demo`), docker-compose with Postgres, Flyway schema + seed, JPA entities +
   repositories, pricing service (pure, unit tested), global error handling, health endpoint,
   Testcontainers base test. Build green.
2. **Core API**: security + JWT, auth/register/login/me, directory lookup, lots/spots read
   endpoints + availability, bookings (create/extend/cancel/checkout/receipt/quote), payments,
   OpenAPI. Unit + integration tests.
3. **Operations API**: manager and admin endpoints, sensors ingestion + simulator, scheduled
   jobs (auto-checkout, overstay/unauthorized detection), notifications via events, analytics.
   Tests.
4. **Frontend**: React app: auth pages, lot browser with live spot grid, booking flow with price
   quote, my bookings (extend/cancel/checkout/receipt), notifications, manager dashboard (stats
   charts, lot/spot controls, bookings table), admin managers page. Tests for key components.
5. **Ship**: Dockerfiles, full `docker-compose.yml`, GitHub Actions CI, README rewrite
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
