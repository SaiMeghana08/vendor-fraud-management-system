# Backend — Fraudulent Vendor Bank-Change Detection Workflow

Spring Boot 4 modular monolith. Runs independently of the frontend on
<http://localhost:9090>.

## Requirements

JDK 21, Maven 3.9+, MySQL 8.

## Configuration

No secret has a default value. The application will not start without these:

| Variable | Purpose |
|---|---|
| `DB_USERNAME`, `DB_PASSWORD` | MySQL credentials |
| `JWT_SECRET` | HS256 signing key, at least 32 characters |
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Optional; default to `localhost`, `3306`, `vendor_fraud_db` |
| `COOLING_OFF_DURATION` | ISO-8601, e.g. `PT24H`. Default 24 hours |
| `COOLING_OFF_CRON` | Scheduler cron. Default every two minutes |
| `BOOTSTRAP_ENABLED`, `BOOTSTRAP_PASSWORD` | Creates the development users. Off by default |

Or copy `src/main/resources/application-local.yml.example` to
`application-local.yml` (git-ignored) and run with
`SPRING_PROFILES_ACTIVE=local`.

## Running

```bash
export DB_USERNAME=vendor_fraud_app
export DB_PASSWORD='your-password'
export JWT_SECRET="$(openssl rand -base64 48)"
export BOOTSTRAP_ENABLED=true
export BOOTSTRAP_PASSWORD='your-dev-password'
export COOLING_OFF_DURATION=PT2M

mvn spring-boot:run
```

Flyway creates the schema on first start.

## Tests

```bash
mvn test     # 98 tests
```

Runs against H2 in MySQL compatibility mode. See
`../docs/DATABASE_SETUP.md` for running the same suite against MySQL 8, and
`../docs/TEST_EXECUTION_REPORT.md` for what that does and does not prove.

## Layout

```
src/main/java/com/vendorfraud/
├── config/        typed configuration + controlled bootstrap
├── common/        error shape, exception handler, masking
├── security/      JWT, principal, filter, active-user guard
├── user/          users, roles, authentication
├── vendor/        vendors and banking records
├── bankchange/    requests, approval tasks, assignment, activation
├── payment/       payments and holds
├── override/      emergency overrides
├── notification/  database-backed notifications
├── audit/         append-only audit trail
├── reference/     row-locked reference generation
└── scheduler/     cooling-off pass
```

Full API in `../docs/API_CONTRACT.md`.
