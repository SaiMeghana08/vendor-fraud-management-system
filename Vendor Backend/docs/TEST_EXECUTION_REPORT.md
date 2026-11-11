# Test execution report

Results from actual runs on 2026-11-11. Nothing in this document is projected,
expected or assumed: every number below came out of a command whose output is
reproduced here.

**Environment**

| | |
|---|---|
| JDK | OpenJDK 21.0.12.1 |
| Maven | Apache Maven 3.9.11 |
| Node | v22.23.4, npm 10.9.9 |
| Backend test database | H2 2.x in MySQL compatibility mode (see limitations) |
| OS | Linux x86_64 |

---

## Backend — `mvn test`

**98 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.**

```
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0 -- BankChangeStatusTransitionTest
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- ApproverAssignmentServiceTest
Tests run:  4, Failures: 0, Errors: 0, Skipped: 0 -- CommandFieldRejectionTest
Tests run:  3, Failures: 0, Errors: 0, Skipped: 0 -- MaskingUtilsTest
Tests run:  6, Failures: 0, Errors: 0, Skipped: 0 -- AuthenticationIntegrationTest
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- BankChangeWorkflowIntegrationTest
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0 -- NegativeControlsIntegrationTest
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0 -- PaymentControlsIntegrationTest
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- EmergencyOverrideIntegrationTest
Tests run:  9, Failures: 0, Errors: 0, Skipped: 0 -- ReassignmentIntegrationTest
Tests run:  5, Failures: 0, Errors: 0, Skipped: 0 -- SchedulerRecoveryIntegrationTest
Tests run:  4, Failures: 0, Errors: 0, Skipped: 0 -- ConcurrencyIntegrationTest

Tests run: 98, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### Compilation

`mvn compile` — **BUILD SUCCESS**, no errors, no deprecation warnings.

---

## Required negative tests

The specification named nineteen scenarios that must be covered. All nineteen
are, and all pass:

| # | Scenario | Test | Result |
|---|---|---|---|
| 1 | Maker selecting an approver | `Negative.makerCannotSelectApprover` | 400 `UNEXPECTED_FIELD`; nothing created |
| 2 | Maker submitting approver ids | `Negative.makerCannotSupplyServerControlledFields`, `Fields.rejectsFirstApproverId` | 400 |
| 3 | Maker self-approval | `Negative.makerCannotSelfApprove` | Excluded from assignment; direct attempt 403 `MAKER_CANNOT_APPROVE` |
| 4 | Same approver approving twice | `Negative.sameApproverCannotApproveBothStages` | Second assignee differs; direct attempt 403 |
| 5 | Out-of-order approvals | `Negative.outOfOrderApprovalIsRejected` | No second task exists; unknown task 404 |
| 6 | Duplicate approval | `Negative.duplicateDecisionIsRejected` | 409 |
| 7 | Invalid role access | `Negative.makerIsLockedOutOfOtherModules` | 403 on payments, holds, audit, overrides |
| 8 | Unauthorized override | `Override.onlyAdministratorMayDecide` | 403 for Maker, Approver, Accounts Payable |
| 9 | Override before dual approval | `Override.overrideRequiresCompletedDualApproval` | 422 `DUAL_APPROVAL_INCOMPLETE` after zero and after one approval |
| 10 | Payment processing under an active hold | `Payment.heldPaymentCannotBeProcessed` | 422 `PAYMENT_NOT_ELIGIBLE` |
| 11 | Cooling-off bypass | `Workflow.schedulerDoesNotActivateEarly` | Scheduler handled 0; status unchanged |
| 12 | Duplicate request | `Negative.duplicateOpenRequestIsRefused` | 422 `DUPLICATE_OPEN_REQUEST` |
| 13 | Concurrent decisions | `Concurrency.concurrentDecisionsProduceOneWinner` | 4 simultaneous: exactly 1 accepted, 3 rejected, 1 second-stage task |
| 14 | Inactive user login | `Auth.inactiveUserCannotLogIn` | 401 |
| 15 | Cross-user notifications | `Negative.notificationsArePrivate` | 404 for another user; 200 for the owner |
| 16 | Unauthorized audit access | `Negative.auditIsAdministratorOnly` | 403 for Approver and AP; 200 for Administrator |
| 17 | Client-supplied payment reference | `Payment.clientSuppliedReferenceRejected` | 400 `UNEXPECTED_FIELD` |
| 18 | Soft-deleted vendor selection | `Negative.softDeletedVendorCannotBeUsed` | Absent from the dropdown; creation 404 |
| 19 | Scheduler recovery after restart | `Scheduler.deadlineMissedWhileDownIsRecovered` | Deadline two hours in the past: next pass activated it |

---

## Frontend

### `npx tsc -b`

Clean. No type errors.

### `npx eslint . --ext ts,tsx`

Clean. No errors, no warnings. `@typescript-eslint/no-explicit-any` is set to
error; there is no `any` in the source.

### `npx vitest run`

**15 tests, 4 files, all passing.**

```
✓ tests/makerForm.test.tsx       (5 tests)
✓ tests/roleNavigation.test.tsx  (3 tests)
✓ tests/formatting.test.ts       (4 tests)
✓ tests/apiClient.test.ts        (3 tests)

Test Files  4 passed (4)
     Tests  15 passed (15)
```

Covered: form-validation rules, the absence of approver/reference/status fields
on the Maker form, the masked read-only current reference, the exact payload
sent to the API, role-based navigation and route guarding, and API error
translation.

### `npm run build`

```
vite v6.4.6 building for production...
✓ 1675 modules transformed.
dist/index.html                   0.43 kB │ gzip:   0.29 kB
dist/assets/index-B-ejYKnb.css   17.13 kB │ gzip:   3.93 kB
dist/assets/index-DnYfPY34.js   373.90 kB │ gzip: 115.86 kB
✓ built in 2.95s
```

Production build succeeds (`tsc -b && vite build`).

---

## Defects found and fixed during testing

Two real bugs, both found by tests rather than by reading:

**1. Field rejection surfaced as HTTP 500.** `UnexpectedFieldException` was
thrown during Jackson deserialisation, so Spring wrapped it in
`HttpMessageNotReadableException` and the generic handler turned it into a 500.
A Maker who tried to name an approver got "an unexpected error occurred" rather
than a clear rejection. Fixed by handling `HttpMessageNotReadableException`,
walking the cause chain and preserving the specific code. Unmapped HTTP methods
were returning 500 for the same reason; handlers for method-not-allowed,
media-type and no-resource were added alongside.

**2. Reference generation ran in the caller's transaction.**
`ReferenceGeneratorService.nextPaymentReference()` delegated to an annotated
`next()` method on the same instance — a self-invocation, which bypasses the
Spring proxy. The `REQUIRES_NEW` propagation never applied, so the row lock was
held for the life of the surrounding business transaction instead of
microseconds, and calling the generator outside a transaction failed outright.
Found by `Concurrency.concurrentReferenceGenerationIsUnique`. Fixed by
annotating each entry point and delegating to a private helper.

---

## Not verified, and why

Stated plainly rather than implied:

**MySQL integration.** The suite runs against H2 in MySQL compatibility mode.
Two things are therefore unproven by automation:

- The Flyway migrations themselves. They are MySQL-specific DDL and H2 does not
  execute them; the test schema is generated from the JPA mappings instead. The
  mappings and the migrations were written to match, and
  `spring.jpa.hibernate.ddl-auto=validate` in the main profile will fail at
  startup against MySQL if they ever diverge — but that check has not been run
  here, because no MySQL server was available in this environment.
- Real InnoDB row-lock behaviour. H2 serialises more aggressively, so the
  concurrency tests prove their invariants (one winner, no duplicate
  references, one activation) without exercising `SELECT ... FOR UPDATE` as
  MySQL implements it.

`docs/DATABASE_SETUP.md` gives the exact configuration for re-running the same
suite against MySQL 8, including the one H2-specific line in `TestFixtures` that
needs swapping.

**End-to-end browser run.** No Playwright or Cypress suite exists. The frontend
is covered by component and unit tests against a mocked API; the full
browser-to-database path has not been exercised by automation.

**Postman collection.** The collection is valid v2.1 JSON and imports, but it
has not been executed against a running instance here — that needs the backend
up with a seeded database. Each request carries its assertions ready to run.

**Load and performance.** Not measured. The concurrency tests use 4 to 8 threads
to prove correctness, not throughput.
