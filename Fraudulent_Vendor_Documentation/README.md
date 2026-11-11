# Fraudulent Vendor Bank-Change Detection Workflow

An enterprise-style control system for the moment a vendor's banking details
change — the moment invoice-redirection fraud depends on. Nothing about a
vendor's payment destination moves without two independent approvals, a
cooling-off window, a revalidation pass, and a separate decision about whether
that vendor's payments may resume.

Every bank account reference in this project is fictional.

---

## What the system actually enforces

| Control | How it is enforced |
|---|---|
| A Maker cannot pick who approves their request | `ApproverAssignmentService` chooses; the form has no approver field, and the API rejects one if sent |
| A Maker cannot approve their own request | Checked against the JWT principal, not the request body |
| Two approvals, by two different people, in order | Second-stage approver is assigned only after first-stage approval, excluding everyone already involved |
| Proposed details stay inert until every control passes | The live `vendor_bank_accounts` row is untouched until activation |
| Cooling-off cannot be skipped | The deadline lives in the database; only the scheduler acts on it |
| Payments stop while details are in question | Holds placed at request time; eligibility recomputed on every payment operation |
| An emergency override is narrow | It shortens cooling-off and does nothing else — not dual approval, not activation, not holds |
| References are never client-supplied | Row-locked sequence table plus unique constraints |
| History is never overwritten | Superseded bank records, decided approvals and audit rows are all retained |

---

## Stack

**Backend** — Java 21, Spring Boot 4.1.2, Spring Web MVC, Spring Data JPA,
Hibernate, Spring Security with JWT, Jakarta Bean Validation, Flyway, MySQL 8,
Maven, JUnit 5 + Mockito + Spring Boot Test.

**Frontend** — React 18, TypeScript, Vite 6, Tailwind CSS, React Router, Axios,
React Hook Form, Zod, Lucide icons, Vitest + Testing Library.

A modular monolith: one deployable, with module boundaries enforced in code.

---

## Running it

### Prerequisites

- JDK 21
- Maven 3.9+
- Node.js 20+
- MySQL 8 (MySQL Workbench optional but assumed in `docs/DATABASE_SETUP.md`)

### 1. Database

```sql
CREATE DATABASE vendor_fraud_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'vendor_fraud_app'@'localhost' IDENTIFIED BY 'choose-your-own-password';
GRANT ALL PRIVILEGES ON vendor_fraud_db.* TO 'vendor_fraud_app'@'localhost';
FLUSH PRIVILEGES;
```

Flyway creates every table on first start. Full walkthrough:
[`docs/DATABASE_SETUP.md`](docs/DATABASE_SETUP.md).

### 2. Backend

```bash
cd backend

export DB_USERNAME=vendor_fraud_app
export DB_PASSWORD='choose-your-own-password'
export JWT_SECRET="$(openssl rand -base64 48)"
export BOOTSTRAP_ENABLED=true
export BOOTSTRAP_PASSWORD='choose-a-dev-password'
export COOLING_OFF_DURATION=PT2M   # shorten the window while demonstrating

mvn spring-boot:run
```

Runs on <http://localhost:9090>. There is no default JWT secret and no default
database password: the application refuses to start without them.

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Runs on <http://localhost:5173>, proxying `/api` to the backend.

### 4. Sign in

The bootstrap creates these accounts, all with `BOOTSTRAP_PASSWORD`:

| Username | Role | Purpose |
|---|---|---|
| `maker.one`, `maker.two` | MAKER | Raise bank-change requests |
| `approver.one`, `approver.two`, `approver.three` | APPROVER | Independent approvals (three, so the second stage always has a candidate) |
| `ap.one` | ACCOUNTS_PAYABLE | Payments, holds, override requests |
| `admin.one` | ADMINISTRATOR | Override decisions, exceptions, audit |
| `inactive.maker` | MAKER (deactivated) | Proves a soft-deleted identity cannot sign in |

There is no registration endpoint and no screen that grants a role. The
bootstrap is the only way a user is created, and it is disabled by default.

---

## Repository layout

```
Fraudulent_Vendor_Bank_Change_System/
├── backend/      Spring Boot Maven project (independently runnable)
├── frontend/     React + Vite + TypeScript project (independently runnable)
├── database/     Schema and seed references for manual setup
├── docs/         Documentation and diagrams
├── postman/      Collection and environment
└── tests/        Acceptance-test notes and manual scenarios
```

---

## Documentation

| Document | What it covers |
|---|---|
| [`docs/PROJECT_DOCUMENTATION.md`](docs/PROJECT_DOCUMENTATION.md) | Step-by-step build, from Step 0 to packaging |
| [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) | Every endpoint, request shape and error code |
| [`docs/BUSINESS_RULE_TRACEABILITY.md`](docs/BUSINESS_RULE_TRACEABILITY.md) | BR-001 to BR-100 against the implementation |
| [`docs/DATABASE_SETUP.md`](docs/DATABASE_SETUP.md) | MySQL and Workbench setup, migrations, verification |
| [`docs/TEST_EXECUTION_REPORT.md`](docs/TEST_EXECUTION_REPORT.md) | Actual results from the actual runs |
| [`docs/SECURITY_DESIGN.md`](docs/SECURITY_DESIGN.md) | Authentication, authorization, separation of duties |
| [`docs/ARCHITECTURE_DIAGRAM.md`](docs/ARCHITECTURE_DIAGRAM.md) | Component architecture |
| [`docs/BUSINESS_WORKFLOW_DIAGRAM.md`](docs/BUSINESS_WORKFLOW_DIAGRAM.md) | The workflow, branches included |
| [`docs/ER_DIAGRAM.md`](docs/ER_DIAGRAM.md) | Entity relationships |

---

## Tests

```bash
cd backend  && mvn test        # 98 tests
cd frontend && npm run test    # 15 tests
cd frontend && npm run build   # typecheck + production build
```

Results as actually run are in
[`docs/TEST_EXECUTION_REPORT.md`](docs/TEST_EXECUTION_REPORT.md), including what
was not verified and why.
