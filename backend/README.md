# CampusPark API (backend)

Spring Boot 3.5 / Java 17 REST API. For the whole stack (Docker Compose, demo accounts, architecture, deployment),
see the [root README](../README.md). The full spec and decision log are in [`../docs/PLAN.md`](../docs/PLAN.md).

## Run from source

```bash
docker compose up -d postgres                              # from the repository root: PostgreSQL 16 on 127.0.0.1:5432

./mvnw spring-boot:run                                     # default profile: reference data only
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo     # demo accounts, ~2 months of history, sensor simulator
```

On Windows (cmd/PowerShell), use `mvnw.cmd` instead of `./mvnw`.

- Health: http://localhost:8080/actuator/health
- API docs: http://localhost:8080/swagger-ui.html

Flyway applies the schema and the reference data (university directory, 3 lots with 130 spots) on startup.

## Profiles

| Profile | Purpose |
|---|---|
| *(default)* | Local development with the dev defaults below |
| `demo` | Seeds the demo accounts (`DemoDataSeeder`) and background drivers with their booking history (`DemoHistorySeeder`, deterministic, runs once on a fresh database), and starts the sensor simulator |
| `prod` | Fails fast unless the JWT secret, sensor key and super-manager password are changed (`ProductionSafetyCheck`); can't be combined with `demo` |
| `test` | Used by the test suite (`src/test/resources/application-test.yml`) |

## Configuration

All settings can be overridden with environment variables. The defaults in `application.yml` are for local
development only.

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `parking`, `parking`, `parking` | PostgreSQL connection |
| `APP_JWT_SECRET` | dev value | HS256 signing key, at least 32 bytes |
| `APP_JWT_EXPIRY` | `PT1H` | Access-token lifetime |
| `APP_SENSORS_API_KEY` | `dev-sensor-key` | Expected `X-Sensor-Key` header |
| `APP_SUPER_MANAGER_EMAIL`, `APP_SUPER_MANAGER_PASSWORD` | `admin@campus-parking.local`, `ChangeMe!2024` | Super manager created on first start |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Origins allowed to call the API cross-origin |
| `APP_DEMO_PASSWORD` | `Demo#2026` | Password of the seeded accounts (`demo` only) |
| `APP_JOBS_ENABLED` | `true` | Scheduled auto-checkout and overstay detection |
| `SERVER_PORT` | `8080` | HTTP port |

## Tests

```bash
./mvnw verify
```

184 tests. The integration tests use Testcontainers (PostgreSQL 16), so Docker must be running.

## Docker image

`Dockerfile` builds with the Maven wrapper (tests are skipped there because CI runs them). The image runs the
layered Spring Boot jar on `eclipse-temurin:17-jre-alpine` as a non-root user, with a `HEALTHCHECK` on
`/actuator/health`. The image defaults to `SPRING_PROFILES_ACTIVE=prod`, and JVM flags come from `JAVA_OPTS`.
