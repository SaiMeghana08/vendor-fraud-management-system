# Project documentation

How this system was built, step by step, from an empty directory to packaged
archives. Written so that a developer who has never seen this conversation can
reproduce the whole thing and understand why each decision was made.

**Contents**

| Step | Title |
|---|---|
| 0 | Prerequisites and environment |
| 1 | Architecture and business rules |
| 2 | Database schema |
| 3 | Backend project skeleton |
| 4 | JWT and role security |
| 5 | Vendor module |
| 6 | Automatic approver assignment |
| 7 | Bank-change workflow |
| 8 | Payment holds |
| 9 | Cooling-off |
| 10 | Emergency overrides |
| 11 | Reassignment |
| 12 | Notifications |
| 13 | Audit |
| 14 | Soft delete |
| 15 | Frontend project |
| 16 | Role dashboards |
| 17 | API integration |
| 18 | Tests |
| 19 | Diagrams |
| 20 | Documentation |
| 21 | Postman collection |
| 22 | Packaging and verification |

---

## Step 0 — Prerequisites and environment

**Objective.** Have a machine that can build and run both halves.

**Business purpose.** None directly; everything after this depends on it.

**Prerequisites**

| Tool | Version used | Check |
|---|---|---|
| JDK | 21.0.12.1 | `java -version` |
| Maven | 3.9.11 | `mvn -v` |
| Node.js | 22.23.4 | `node -v` |
| npm | 10.9.9 | `npm -v` |
| MySQL | 8.x | `mysql --version` |

**Commands**

```bash
java -version && mvn -v && node -v && npm -v
```

**Expected result.** Java reports 21 or later; Maven 3.9+; Node 20+.

**Actual verified result.** OpenJDK 21.0.12.1, Maven 3.9.11, Node v22.23.4,
npm 10.9.9. MySQL was not available in the build environment, which shaped
Step 18 — see the limitations there and in `TEST_EXECUTION_REPORT.md`.

**Common errors.** A JDK below 21 fails on records and pattern matching. Maven
below 3.9 cannot resolve the Spring Boot 4 parent.

**Completion status.** Complete.

---

## Step 1 — Architecture and business rules

**Objective.** Settle the shape before writing code.

**Business purpose.** The system exists to stop invoice-redirection fraud: a
convincing request to change a vendor's bank details, followed by the next
payment run. Every design decision answers "what would an attacker need, and
what stops them?"

**Architecture.** A modular monolith. One deployable, with each capability in
its own package talking to its peers through services.

The reason is transactional. Creating a bank-change request must do five things
atomically: persist the request, generate a reference, derive the Maker from the
token, place payment holds, and assign an approver. If any part fails, none may
stand — a request that exists with no holds is exactly the window fraud needs.
Across services that is a distributed-transaction problem; in one deployable it
is a `@Transactional` method.

**Rules settled here**

1. Four roles, no permissions table.
2. The Maker never influences who approves.
3. Two approvals, two different people, in order.
4. Proposed details stay inert until every control passes.
5. Cooling-off is scheduler-controlled, with no UI bypass.
6. Payments stop while details are in question, and resume only by a separate
   justified decision.
7. The emergency override shortens cooling-off and does nothing else.
8. Every reference is backend-generated.
9. History is retained, never overwritten.

**Files created.** None yet; this step produced the decisions recorded in
`ARCHITECTURE_DIAGRAM.md` and `BUSINESS_RULE_TRACEABILITY.md`.

**Completion status.** Complete.

---

## Step 2 — Database schema

**Objective.** A normalised MySQL 8 schema that enforces the rules at the
storage layer, not merely in application code.

**Business purpose.** Service-layer checks are readable; database constraints
are unbreakable. A race condition that slips past a check is stopped by an
index.

**Files created**

```
backend/src/main/resources/db/migration/V1__initial_schema.sql
backend/src/main/resources/db/migration/V2__reference_data.sql
backend/src/main/resources/db/migration/V3__synthetic_seed_data.sql
database/schema/00_create_database.sql
```

**Design explanation.** Fourteen tables. Three decisions carry most of the
weight:

*Marker columns.* MySQL has no partial unique index, so `active_marker` and
`open_marker` hold `1` while a row is active or open and `NULL` once it is not.
A unique index permits many `NULL`s, which gives: one active banking record per
vendor, one open request per vendor, one pending task per stage, one active hold
per payment and request.

*Soft delete where it belongs.* Master records (`users`, `vendors`,
`vendor_bank_accounts`) carry `is_active`, `deleted_at`, `deleted_by`.
Transactional records get lifecycle statuses instead — a completed approval is a
historical fact, and the schema offers no way to retract one.

*No user seeding.* `V3` seeds vendors and banking records but never a user,
because a password hash does not belong in version control.

**Database operations**

```sql
CREATE DATABASE vendor_fraud_db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'vendor_fraud_app'@'localhost' IDENTIFIED BY '...';
GRANT ALL PRIVILEGES ON vendor_fraud_db.* TO 'vendor_fraud_app'@'localhost';
```

**Expected result.** Flyway applies V1-V3 on first start; `SHOW TABLES` lists 14
tables plus `flyway_schema_history`.

**Actual verified result.** The migrations are written and syntactically
complete. They were **not executed** in this environment — no MySQL server was
available. The equivalent schema, generated from the JPA mappings, was created
and exercised by all 98 backend tests. `DATABASE_SETUP.md` gives the exact steps
to run the migrations and verify them.

**Common errors.** `Unknown database` means Step 2's `CREATE DATABASE` was
skipped. A Flyway checksum mismatch means a committed migration was edited —
restore it, or drop the development database.

**Completion status.** Complete; migration execution against MySQL is unverified
and marked as such.

---

## Step 3 — Backend project skeleton

**Objective.** A Maven project that compiles against Spring Boot 4.

**Files created.** `backend/pom.xml`,
`backend/src/main/java/com/vendorfraud/VendorFraudApplication.java`,
`application.yml`, `application-local.yml.example`.

**Dependencies.** `spring-boot-starter-web`, `-data-jpa`, `-security`,
`-validation`; `flyway-core` and `flyway-mysql`; `mysql-connector-j`; JJWT
0.12.6; for tests `spring-boot-starter-test`, `spring-boot-webmvc-test`,
`spring-security-test`, H2.

**Implementation explanation.** Spring Boot 4.1.2 against Java 21.
`@ConfigurationPropertiesScan` for the typed config records, `@EnableScheduling`
for the cooling-off pass.

Configuration carries no secret. `spring.datasource.password` is `${DB_PASSWORD}`
with no default, and `app.security.jwt.secret` is `${JWT_SECRET}` with none
either, so the application fails to start rather than running on a well-known
key.

**Commands**

```bash
cd backend && mvn -B dependency:resolve && mvn -B compile
```

**Expected result.** BUILD SUCCESS.

**Actual verified result.** BUILD SUCCESS — after two corrections worth
recording, because both will bite anyone upgrading to Spring Boot 4:

1. **Spring Boot 4 ships Jackson 3.** `com.fasterxml.jackson.databind` is no
   longer on the compile classpath; the package is `tools.jackson.databind`.
   Annotations stay at `com.fasterxml.jackson.annotation`. A hand-written
   `ObjectMapper` bean was removed; Jackson 3 handles `java.time` natively.
2. **`spring.jackson.serialization.write-dates-as-timestamps` no longer
   exists.** Jackson 3 writes ISO-8601 by default, and the stale property made
   the context fail to start. Removed.

**Troubleshooting.** `package com.fasterxml.jackson.databind does not exist` →
Jackson 3; change the import. `Failed to bind properties under
'spring.jackson.serialization'` → remove the obsolete key.

**Completion status.** Complete.

---

## Step 4 — JWT and role security

**Objective.** Stateless authentication and role-based authorization.

**Business purpose.** Everything downstream depends on knowing, reliably, who is
acting. Separation of duties is meaningless if identity can be asserted in a
request body.

**Files created**

```
config/JwtProperties.java  config/BootstrapProperties.java  config/DevDataBootstrap.java
security/AppUserPrincipal.java  security/JwtService.java
security/JwtAuthenticationFilter.java  security/ActiveUserGuard.java
security/CurrentUserService.java  security/SecurityConfig.java
user/User.java  user/Role.java  user/RoleName.java  user/UserRepository.java
user/RoleRepository.java  user/AuthService.java  user/AuthController.java
user/dto/LoginRequest.java  user/dto/LoginResponse.java  user/dto/UserProfileResponse.java
```

**Implementation explanation.** HS256 JWTs carry subject, user id, name and
roles. `JwtAuthenticationFilter` verifies the token, then `ActiveUserGuard`
re-checks the database.

That second check is the point. A valid signature proves a token was issued, not
that the person still works here. Without it, a soft-deleted user keeps acting
until their token expires — up to eight hours of a revoked identity still
approving payments.

`CurrentUserService` is the single source of "who is acting". Every
separation-of-duties check reads from it, never from a request field.

`DevDataBootstrap` is the only path by which a user is created. It is disabled
by default, does nothing without an explicitly configured password, never
modifies an existing user, and has no HTTP surface.

**API**

```
POST /api/auth/login  → 200 with accessToken, 401 otherwise
GET  /api/auth/me     → the caller's own profile
```

**Expected result.** Valid credentials return a token; invalid ones and
deactivated accounts both return 401.

**Actual verified result.** `AuthenticationIntegrationTest` — 6 tests, all
passing, including "no registration endpoint exists".

**Common errors.** Application exits on startup → `JWT_SECRET` unset or shorter
than 32 characters; generate one with `openssl rand -base64 48`.

**Completion status.** Complete.

---

## Step 5 — Vendor module

**Objective.** Serve active vendors and their masked current banking record.

**Business purpose.** The vendor dropdown is the workflow's entry point. A
soft-deleted vendor appearing there is a loophole, so the exclusion lives in the
repository query rather than in a filter a caller might forget.

**Files created.** `vendor/Vendor.java`, `VendorBankAccount.java`,
`BankAccountStatus.java`, both repositories, `VendorService.java`,
`VendorController.java`, `dto/VendorResponse.java`,
`dto/VendorBankAccountResponse.java`, `common/MaskingUtils.java`.

**Implementation explanation.** `VendorRepository.searchActive` filters on
`active = true AND deletedAt IS NULL` in the query itself.
`VendorBankAccountResponse` masks the account reference on the way out: the
unmasked value never leaves the service layer, on any path, including audit
output.

**API**

```
GET /api/vendors?search=                      → active vendors only
GET /api/vendors/{id}/current-bank-account    → masked, or null
GET /api/vendors/{id}/bank-account-history    → includes superseded records
```

**Actual verified result.** `MaskingUtilsTest` (3 tests) and
`Negative.softDeletedVendorCannotBeUsed` pass.

**Completion status.** Complete.

---

## Step 6 — Automatic approver assignment

**Objective.** The backend, and only the backend, decides who approves.

**Business purpose.** This is the control the whole system turns on. A Maker who
can choose their approver can choose a colleague, and dual approval becomes
theatre.

**Files created.** `bankchange/ApproverAssignmentService.java`,
`ApprovalTask.java`, `ApprovalStage.java`, `ApprovalTaskStatus.java`,
`ApprovalAssignmentHistory.java`, `AssignmentAction.java`, two repositories.

**Implementation explanation.** Eligibility, in order: active user holding
APPROVER; not the Maker; and for the second stage, nobody who has held or
decided any stage of this request.

Among the eligible, the one with the fewest pending tasks wins; ties break on
the lowest user id. Determinism matters for two reasons: the behaviour is
testable, and an assignment can be explained after the fact — "why did Priya get
this?" has an answer.

The second-stage exclusion is built from `findAllInvolvedAssigneeIds`, not from
"whoever approved first". That closes a subtle hole: if the first task were
reassigned away from someone, a narrower check would let them take the second
stage.

An empty eligible set raises `NO_ELIGIBLE_APPROVER`. The request keeps its
status and waits. Nothing auto-approves, and no stage is skipped — a workflow
that degrades when understaffed is a workflow an attacker can arrange to
understaff.

**Actual verified result.** `ApproverAssignmentServiceTest` — 9 tests covering
Maker exclusion (including a dual-role user), least-loaded selection, stable
tie-breaking, second-stage independence, the no-eligible-approver stop, and
audit plus notification on every assignment.

**Completion status.** Complete.

---

## Step 7 — Bank-change workflow

**Objective.** Creation, the two approval stages, and the state machine.

**Business purpose.** The core of the system: a proposed change that cannot
become a real change without passing every control.

**Files created.** `BankChangeRequest.java`, `BankChangeStatus.java`,
`BankChangeRequestRepository.java`, `BankChangeRequestService.java`,
`ApprovalService.java`, `BankChangeQueryService.java`,
`BankChangeRequestController.java`, `ApprovalController.java`, and the DTOs —
including `CreateBankChangeRequestCommand`, which carries the field rejection.

**Implementation explanation.** `BankChangeStatus` declares the permitted
transitions and `BankChangeRequest.transitionTo` is the only place a status
changes, so no service can move a request sideways.

`CreateBankChangeRequestCommand` uses `@JsonAnySetter` to reject any property
that is not one of the six business fields. Ignoring unknown fields — the usual
default — would let a request that tried to name its own approver appear to
succeed, and nobody would learn it had tried.

A decision takes a pessimistic write lock on both the task and the request, so
two approvers clicking at once cannot both succeed. Repeating a decision is a
409, not a silent second approval.

**API**

```
POST /api/bank-change-requests              → 201, six fields only
POST /api/approvals/tasks/{id}/decision     → 200, stage from the task
```

**Actual verified result.** `BankChangeWorkflowIntegrationTest` (9) and
`NegativeControlsIntegrationTest` (15) pass. Proven: creation leaves the live
banking record untouched; the first approver is never the Maker; only one task
exists until the first approval; the second approver is a different person;
rejection terminates the workflow.

**Completion status.** Complete.

---

## Step 8 — Payment holds

**Objective.** Stop money moving while details are in question.

**Business purpose.** The approval workflow protects the *record*. Holds protect
the *money* — and they are deliberately not wired to the workflow's outcome.

**Files created.** `payment/Payment.java`, `PaymentHold.java`, `PaymentStatus`,
`HoldStatus`, both repositories, `PaymentHoldService.java`,
`PaymentService.java`, controllers and DTOs.

**Implementation explanation.** Holds are placed inside the request-creation
transaction, before anyone is asked to look at the request. A payment raised
while a request is open is held immediately, so timing cannot produce an
unheld payment.

Nothing releases a hold except `PaymentHoldService.releaseHold`: not activation,
not rejection, and above all not an approved emergency override. The release
re-checks every condition at release time, requires a justification of at least
ten characters, and records actor, timestamp and justification.

Eligibility is recomputed from live state on every call and immediately before
processing, under a row lock. A cached flag would be a stale answer to the only
question that matters.

**Actual verified result.** `PaymentControlsIntegrationTest` — 14 tests,
including `activationDoesNotReleaseHolds` and `rejectionDoesNotReleaseHolds`.

**Completion status.** Complete.

---

## Step 9 — Cooling-off

**Objective.** A window between approval and activation that cannot be skipped.

**Business purpose.** Dual approval can be defeated by a convincing enough story
told twice. A delay gives the real vendor time to notice, and gives the
organisation time to call back on a known number.

**Files created.** `config/CoolingOffProperties.java`,
`scheduler/CoolingOffScheduler.java`, `bankchange/BankActivationService.java`.

**Implementation explanation.** The deadline lives in the database, which is
what makes restart recovery a property of the design rather than a mechanism:
each pass asks "which requests are in `COOLING_OFF` with a deadline in the
past?". A process that was down for an hour finds more rows next time. Nothing
is missed and nothing needs replaying.

Each request is handled in its own transaction under a row lock, so a pass is
idempotent and one bad row cannot poison the batch. The scheduled method catches
everything: a scheduled task must never die on an unexpected failure.

Revalidation happens at activation, not at approval, because conditions change
during the window — the vendor may be deactivated, an approval may be missing.
Failure moves the request to `ACTIVATION_BLOCKED` for human review rather than
forcing it through, and later passes leave it blocked.

Activation supersedes rather than overwrites. The old record keeps its
`superseded_at`; the new one records `source_request_id`, so any live banking
detail traces back to the request and evidence that produced it.

**Actual verified result.** `SchedulerRecoveryIntegrationTest` (5) and the
scheduler tests in `BankChangeWorkflowIntegrationTest`. Proven: no early
activation; a two-hour-late deadline is recovered; repeated passes are
idempotent; a batch handles several requests; a vendor deactivated during the
window blocks activation.

**Completion status.** Complete.

---

## Step 10 — Emergency overrides

**Objective.** A narrow, documented exception to cooling-off.

**Business purpose.** Real payroll runs are genuinely urgent. An exception path
that does not exist becomes an exception path implemented badly under pressure —
so it exists, it is narrow, and it is loud.

**Files created.** `override/EmergencyOverride.java`, `OverrideStatus.java`,
repository, `EmergencyOverrideService.java`, controller, DTOs.

**Implementation explanation.** Requested by Accounts Payable, decided by an
Administrator who is not the requester. Requires completed dual approval. The
documented transition:

```
COOLING_OFF (deadline in the future)
  --[override APPROVED]-->
COOLING_OFF (deadline = now, cooling_off_override_id set)
  --[scheduler revalidates, as for any other request]-->
ACTIVATED | ACTIVATION_BLOCKED
```

The request does not jump to `ACTIVATED`. It becomes *due*, and the same
revalidation runs. The override cannot bypass dual approval, cannot activate
unapproved details, cannot release a hold, and cannot relax eligibility.

Conditions are re-checked at decision time as well as at request time: the
request may have moved on while the override sat in the queue.

**Actual verified result.** `EmergencyOverrideIntegrationTest` — 9 tests,
including `approvedOverrideDoesNotReleaseHolds`, which proves the activated
request still cannot be paid.

**Completion status.** Complete.

---

## Step 11 — Reassignment

**Objective.** Move a pending task when an approver is unavailable, without
weakening independence.

**Business purpose.** People take leave. A workflow that stalls gets worked
around; a workflow with a controlled reassignment path does not.

**Implementation explanation.** `ApproverAssignmentService.reassign` closes the
pending task as `REASSIGNED` — never deletes it — and opens a fresh one. Every
eligibility rule is re-checked: active, holds APPROVER, not the Maker, not the
current assignee, not already involved. The reason is mandatory, and both the
incoming and outgoing approver are notified.

**Actual verified result.** `ReassignmentIntegrationTest` — 9 tests, including
the Maker being refused as a replacement and the first approver being refused
for the second stage.

**Completion status.** Complete.

---

## Step 12 — Notifications

**Objective.** Tell people what needs them, privately.

**Implementation explanation.** `NotificationService.notifyUser` uses
`Propagation.MANDATORY`, so delivery joins the transaction that caused it. If
the workflow step rolls back, so does the notification — there is no window in
which a user is told about a state the database never reached.

Every read and update is scoped by recipient at the repository level. Asking for
another user's notification returns 404, indistinguishable from one that does
not exist.

**Actual verified result.** `Negative.notificationsArePrivate` passes, as does
`Assignment.assignmentIsAuditedAndNotified`.

**Completion status.** Complete.

---

## Step 13 — Audit

**Objective.** An append-only record of everything that matters.

**Implementation explanation.** `AuditLog` has no setter and no update method.
`AuditController` exposes only `GET`, and only to an Administrator. Business
events join the caller's transaction; security events such as a failed login use
`REQUIRES_NEW`, because they must persist exactly when the surrounding operation
fails.

Blocked separation-of-duties attempts are recorded as
`SEPARATION_OF_DUTIES_VIOLATION_BLOCKED`. A refusal is a signal, and signals
belong in the trail.

Account references inside audit reasons are masked on the way out, so even an
Administrator reading the trail does not see a full banking reference.

**Actual verified result.** `Workflow.auditTrailCoversTheRun` asserts the
presence of creation, both approvals, cooling-off start and activation.
`Negative.auditHasNoWriteEndpoints` confirms no write path exists.

**Completion status.** Complete.

---

## Step 14 — Soft delete

**Objective.** Deactivate master records without losing history.

**Implementation explanation.** `users`, `vendors` and `vendor_bank_accounts`
carry `is_active`, `deleted_at`, `deleted_by`. Deactivating a user preserves
every approval they ever made and every foreign key pointing at them; what
changes is that they can no longer sign in or act.

Transactional records are never soft-deleted. `VendorService.deactivateVendor`
refuses while unresolved work would strand the workflow.

**Actual verified result.** `Auth.inactiveUserCannotLogIn` and
`Negative.softDeletedVendorCannotBeUsed` pass.

**Completion status.** Complete.

---

## Step 15 — Frontend project

**Objective.** A Vite + React + TypeScript + Tailwind application.

**Files created.** `package.json`, `vite.config.ts`, `vitest.config.ts`, the
three `tsconfig` files, `tailwind.config.js`, `postcss.config.js`,
`.eslintrc.cjs`, `index.html`, `src/main.tsx`, `src/App.tsx`, `src/index.css`,
`src/types/index.ts`, `src/api/client.ts`, `src/api/endpoints.ts`.

**Implementation explanation.** The Vite proxy sends `/api` to
`http://localhost:9090`, so the UI never hardcodes an origin and development
needs no CORS preflight.

`src/types/index.ts` mirrors the backend DTOs exactly. `src/api/endpoints.ts` is
the complete list of calls the UI can make — and there is deliberately no helper
for choosing an approver or supplying a reference, because no such call exists.

The axios client attaches the bearer token and, on a 401, clears the session and
returns the user to login rather than leaving them clicking into failures.

**Commands**

```bash
cd frontend && npm install && npx tsc -b
```

**Actual verified result.** Clean typecheck, after one correction: Vitest 2.x
bundles its own Vite 5, which clashes with Vite 6's types. Upgrading to Vitest 3
and moving the test config into `vitest.config.ts` resolved it.

**Completion status.** Complete.

---

## Step 16 — Role dashboards

**Objective.** Four dashboards, each showing only what its role can do.

**Files created.** `components/AppLayout.tsx`, `RequireRole.tsx`,
`NotificationBell.tsx`, `ReassignDialog.tsx`, the seven `components/ui/*`
primitives, `pages/LoginPage.tsx`, `pages/RequestDetailPage.tsx`,
`pages/maker/*`, `pages/approver/*`, `pages/ap/*`, `pages/admin/*`.

**Implementation explanation.** The Maker form shows exactly nine controls: the
vendor search, the vendor dropdown, the masked read-only current reference, the
four proposed fields, the reason, the evidence reference, and Submit. There is
no approver field — not hidden, not disabled, not present.

`RequireRole` hides what a role cannot use. It is a usability measure, not a
security boundary: every rule it reflects is enforced again on the server, and
the tests prove it by calling the API directly with the wrong role.

`CountdownBadge` ticks locally for readability but counts towards the backend's
deadline. There is no control anywhere in the UI that can shorten or skip it.

**Actual verified result.** `makerForm.test.tsx` asserts the absence of
approver, reference, status, stage and maker fields.

**Completion status.** Complete.

---

## Step 17 — API integration

**Objective.** Every operational value on screen comes from the backend.

**Implementation explanation.** No mocked or hardcoded operational data exists
in the frontend. `useAsync` gives every screen the three states it needs —
loading, loaded, and an error the user can act on — and errors carry the
backend's own message and business code rather than a generic string.

Where the backend returns field-level validation errors, `CreateBankChangeForm`
attaches them to the matching field.

**Actual verified result.** `apiClient.test.ts` proves the error translation
preserves code, message and field errors, and explains an unreachable backend
rather than failing silently.

**Completion status.** Complete.

---

## Step 18 — Tests

**Objective.** Prove the controls, especially the negative cases.

**Files created.** 12 backend test classes plus `TestFixtures` and
`IntegrationTestBase`; 4 frontend test files plus `setup.ts`.

**Implementation explanation.** Integration tests drive the workflow through
HTTP exactly as the UI does, authenticating through `POST /api/auth/login`, so
the token and authorization paths are exercised on every call rather than
bypassed with a mock principal.

The backend suite runs against H2 in MySQL compatibility mode with the schema
generated from the JPA mappings, because no MySQL server was available here.
What that does and does not prove is set out in `TEST_EXECUTION_REPORT.md`.

**Commands**

```bash
cd backend  && mvn test
cd frontend && npx tsc -b && npx eslint . --ext ts,tsx && npx vitest run && npm run build
```

**Expected result.** All green.

**Actual verified result.** Backend: **98 tests, 0 failures, 0 errors**.
Frontend: **15 tests passing**, clean typecheck, clean lint, successful
production build. Two real defects were found and fixed in the process — field
rejection surfacing as HTTP 500, and a Spring proxy self-invocation in the
reference generator. Both are described in `TEST_EXECUTION_REPORT.md`.

**Completion status.** Complete, with MySQL integration explicitly unverified.

---

## Step 19 — Diagrams

**Objective.** Architecture, workflow and ER diagrams, as source and rendered
images.

**Files created.** `docs/diagrams/{architecture,business-workflow,er-diagram}`
as `.mmd`, `.svg` and `.png`.

**Commands**

```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i architecture.mmd -o architecture.svg -b white
mmdc -i architecture.mmd -o architecture.png -b white -s 2
```

**Actual verified result.** All three rendered successfully in both formats and
were inspected.

**Completion status.** Complete.

---

## Step 20 — Documentation

**Files created.** `README.md`, and in `docs/`: this file, `API_CONTRACT.md`,
`BUSINESS_RULE_TRACEABILITY.md`, `DATABASE_SETUP.md`,
`TEST_EXECUTION_REPORT.md`, `SECURITY_DESIGN.md`, `ARCHITECTURE_DIAGRAM.md`,
`BUSINESS_WORKFLOW_DIAGRAM.md`, `ER_DIAGRAM.md`, plus `database/README.md` and
`tests/README.md`.

**Completion status.** Complete.

---

## Step 21 — Postman collection

**Files created.** `postman/Fraudulent_Vendor_API.postman_collection.json` (v2.1,
10 folders, 64 requests), `postman/Fraudulent_Vendor_Environment.postman_environment.json`.

**Implementation explanation.** Folder 1 captures a bearer token per role into
collection variables; later folders chain through variables captured by the ones
before them. Each negative request says in its description which role's token to
run it with — several exist precisely to be run with the *wrong* role.

The environment ships with an empty `password`, marked as a secret.

**Actual verified result.** Both files parse as valid JSON and match the v2.1
schema. The collection has **not** been executed against a running instance
here, which would need a live backend and database.

**Completion status.** Complete; execution unverified.

---

## Step 22 — Packaging and verification

**Objective.** Three archives, with nothing in them that should not be.

**Commands**

```bash
zip -r Fraudulent_Vendor_Backend_Complete.zip backend \
  -x '*/target/*' '*.class' '*/application-local.yml'
zip -r Fraudulent_Vendor_Frontend_Complete.zip frontend \
  -x '*/node_modules/*' '*/dist/*' '*/.env*'
zip -r Fraudulent_Vendor_Documentation.zip docs postman README.md database tests
unzip -t <each archive>
```

**Expected result.** Three archives, each passing an integrity test, with no
`target/`, `node_modules/`, `dist/`, `.env` or secret inside.

**Actual verified result.** See the final section of
`TEST_EXECUTION_REPORT.md` and the packaging summary in the delivery message:
all three archives were created, tested with `unzip -t`, and their contents
listed and checked for excluded paths and for any literal secret.

**Completion status.** Complete.
