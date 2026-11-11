# Database setup

MySQL 8, database `vendor_fraud_db`. Flyway owns the schema — you create an
empty database and a user, and the application does the rest on first start.

---

## 1. Create the database and user

In MySQL Workbench, open a query tab on your local instance and run:

```sql
CREATE DATABASE vendor_fraud_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'vendor_fraud_app'@'localhost'
  IDENTIFIED BY 'choose-your-own-password';

GRANT ALL PRIVILEGES ON vendor_fraud_db.* TO 'vendor_fraud_app'@'localhost';
FLUSH PRIVILEGES;
```

Or from the command line:

```bash
mysql -u root -p < database/schema/00_create_database.sql
```

Pick your own password. Nothing in this repository contains one.

---

## 2. Point the application at it

```bash
export DB_HOST=localhost
export DB_PORT=3306
export DB_NAME=vendor_fraud_db
export DB_USERNAME=vendor_fraud_app
export DB_PASSWORD='choose-your-own-password'
export JWT_SECRET="$(openssl rand -base64 48)"
```

Or copy `backend/src/main/resources/application-local.yml.example` to
`application-local.yml` (git-ignored), fill it in, and run with
`SPRING_PROFILES_ACTIVE=local`.

---

## 3. Run the migrations

They run automatically on start:

```bash
cd backend && mvn spring-boot:run
```

To run them without starting the application:

```bash
mvn flyway:migrate \
  -Dflyway.url=jdbc:mysql://localhost:3306/vendor_fraud_db \
  -Dflyway.user=vendor_fraud_app \
  -Dflyway.password='choose-your-own-password'
```

### What each migration does

| Version | File | Creates |
|---|---|---|
| V1 | `V1__initial_schema.sql` | All 14 tables, foreign keys, unique constraints, indexes, check constraints |
| V2 | `V2__reference_data.sql` | The four roles and the three reference sequences |
| V3 | `V3__synthetic_seed_data.sql` | Six synthetic vendors (one deliberately soft-deleted) and their active banking records |

**No migration creates a user.** A password hash does not belong in version
control, so users come from the bootstrap component described below.

---

## 4. Create the development users

```bash
export BOOTSTRAP_ENABLED=true
export BOOTSTRAP_PASSWORD='choose-a-dev-password'
mvn spring-boot:run
```

`DevDataBootstrap` creates eight accounts, hashing the configured password with
BCrypt. It is inert unless both variables are set, never modifies an existing
user, and has no HTTP surface. Turn it off again once the users exist.

| Username | Role |
|---|---|
| `maker.one`, `maker.two` | MAKER |
| `approver.one`, `approver.two`, `approver.three` | APPROVER |
| `ap.one` | ACCOUNTS_PAYABLE |
| `admin.one` | ADMINISTRATOR |
| `inactive.maker` | MAKER, deactivated on purpose |

---

## 5. Verify

```sql
USE vendor_fraud_db;

-- 14 tables plus flyway_schema_history
SHOW TABLES;

-- All four migrations applied, success = 1
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;

-- Four roles, no permissions table anywhere
SELECT name FROM roles;

-- Five active vendors; VND-9001 is soft-deleted
SELECT vendor_code, name, is_active, deleted_at FROM vendors;

-- One ACTIVE banking record per vendor
SELECT v.vendor_code, b.bank_name, b.status, b.active_marker
  FROM vendor_bank_accounts b JOIN vendors v ON v.id = b.vendor_id;

-- Reference sequences ready
SELECT * FROM reference_sequences;
```

### Useful queries while the workflow runs

```sql
-- Where every request stands
SELECT request_reference, status, cooling_off_end_at, activated_at
  FROM bank_change_requests ORDER BY id DESC;

-- Who holds which approval, and who already decided
SELECT r.request_reference, t.stage, u.username, t.status, t.decided_at
  FROM approval_tasks t
  JOIN bank_change_requests r ON r.id = t.bank_change_request_id
  JOIN users u ON u.id = t.assignee_id
 ORDER BY t.id;

-- Confirm the two approvals were two different people
SELECT r.request_reference, COUNT(DISTINCT t.assignee_id) AS distinct_approvers
  FROM approval_tasks t
  JOIN bank_change_requests r ON r.id = t.bank_change_request_id
 WHERE t.status = 'APPROVED'
 GROUP BY r.request_reference;

-- Banking history for a vendor: current plus every superseded record
SELECT account_reference, status, activated_at, superseded_at, source_request_id
  FROM vendor_bank_accounts
 WHERE vendor_id = 1 ORDER BY id;

-- Active holds and what caused them
SELECT h.id, p.payment_reference, h.reason, h.status
  FROM payment_holds h JOIN payments p ON p.id = h.payment_id
 WHERE h.status = 'ACTIVE';

-- The full trail of one request
SELECT created_at, actor_username, action, workflow_state, reason
  FROM audit_logs
 WHERE entity_type = 'BANK_CHANGE_REQUEST' AND entity_id = 1
 ORDER BY id;
```

---

## Running the backend tests against MySQL

The suite runs against H2 in MySQL mode by default, which is fast and needs no
server. H2 cannot exercise the Flyway migrations (they are MySQL-specific DDL)
and it serialises more aggressively than InnoDB, so the concurrency tests prove
their invariants there without exercising real row locks.

To run the same suite against MySQL:

1. Create a second database:

   ```sql
   CREATE DATABASE vendor_fraud_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
   GRANT ALL PRIVILEGES ON vendor_fraud_test.* TO 'vendor_fraud_app'@'localhost';
   ```

2. Replace `backend/src/test/resources/application-test.yml` with:

   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/vendor_fraud_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
       username: vendor_fraud_app
       password: ${DB_PASSWORD}
     jpa:
       hibernate:
         ddl-auto: validate
     flyway:
       enabled: true
       clean-disabled: false
   app:
     security:
       jwt:
         secret: ${JWT_SECRET}
         issuer: vendor-fraud-detection
     cooling-off:
       duration: PT24H
       scheduler:
         cron: "0 0 5 31 2 *"
     bootstrap:
       enabled: false
   ```

3. Run `mvn test`. `TestFixtures.reset()` uses
   `SET REFERENTIAL_INTEGRITY FALSE`, which is H2 syntax — swap it for
   `SET FOREIGN_KEY_CHECKS = 0` / `= 1` when running against MySQL.

---

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Access denied for user` | Wrong credentials or host | Check `DB_USERNAME`/`DB_PASSWORD`; the grant is for `'...'@'localhost'` |
| `Unknown database 'vendor_fraud_db'` | Step 1 skipped | Create the database |
| `Flyway ... checksum mismatch` | A committed migration was edited | Restore the file, or drop the dev database and re-migrate |
| `Schema-validation: missing table` | Migrations have not run | Check `flyway.enabled` and the Flyway history table |
| Application exits with a JWT secret error | `JWT_SECRET` unset or shorter than 32 characters | Generate one with `openssl rand -base64 48` |
| `Public Key Retrieval is not allowed` | MySQL 8 `caching_sha2_password` | The JDBC URL already sets `allowPublicKeyRetrieval=true`; use it |
| Cooling-off never completes | Scheduler cron or window | Check `app.cooling-off.scheduler.cron`; set `COOLING_OFF_DURATION=PT2M` to demonstrate |
| No approver assigned, `NO_ELIGIBLE_APPROVER` | Too few active approvers | At least two besides the Maker are needed for two stages |
