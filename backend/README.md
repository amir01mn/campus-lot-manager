# Campus Parking — backend

Spring Boot 3.5 / Java 17 API. See [`../docs/PLAN.md`](../docs/PLAN.md) for the full spec.

## Run locally

```bash
# from the repository root: start PostgreSQL 16
docker compose up -d postgres

# from backend/
./mvnw spring-boot:run                                     # default profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo     # demo profile
```

Windows (cmd/PowerShell): use `mvnw.cmd` instead of `./mvnw`.

- Health: http://localhost:8080/actuator/health
- API docs: http://localhost:8080/swagger-ui.html

Flyway applies the schema and reference data (university directory, 3 lots with spots) on startup.

## Configuration

Database settings come from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`
(defaults: `localhost:5432/parking`, user/password `parking`). Other overridable settings:
`APP_JWT_SECRET`, `APP_JWT_EXPIRY`, `APP_SENSORS_API_KEY`, `APP_CORS_ALLOWED_ORIGINS`,
`APP_SUPER_MANAGER_EMAIL`, `APP_SUPER_MANAGER_PASSWORD`. The defaults in `application.yml` are for
local development only.

## Tests

```bash
./mvnw verify
```

Integration tests use Testcontainers (PostgreSQL 16), so Docker must be running.
