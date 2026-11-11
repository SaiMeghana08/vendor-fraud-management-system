# Security design

The threat this system exists for is invoice-redirection fraud: someone
persuades an organisation to change a vendor's payment destination, then waits
for the next payment run. Every control below is aimed at that, and most of them
assume the attacker has a valid login.

---

## Authentication

**Passwords** are hashed with BCrypt at strength 12. Nothing outside
`AuthService` reads a hash, and no endpoint returns one.

**Tokens** are HS256 JWTs carrying the subject, user id, full name and roles,
signed with a key supplied through configuration. There is no default secret:
`JwtProperties` requires at least 32 characters and the application fails to
start without it. The same holds for the database password.

**Every request** is re-checked:

1. `JwtAuthenticationFilter` verifies signature, issuer and expiry.
2. `ActiveUserGuard` loads the user and confirms they are still active.

Step 2 matters. A valid signature proves only that a token was issued; it says
nothing about whether that person still works here. A soft-deleted user stops
being able to act on the next request, not whenever their token expires.

**Login failures** are recorded distinctly — unknown username, wrong password,
deactivated account — while the response to the caller is identical in all
three cases. The auditor can tell them apart; the attacker cannot.

**There is no registration endpoint** and no endpoint that grants a role. Users
come from `DevDataBootstrap`, which is disabled by default and does nothing
without an explicitly configured password.

---

## Authorization

Role-based, with exactly four roles: `MAKER`, `APPROVER`, `ACCOUNTS_PAYABLE`,
`ADMINISTRATOR`. No permissions table, no permission entity, no permission
check.

Authorization happens at three depths:

| Depth | Mechanism | Catches |
|---|---|---|
| URL | `SecurityConfig` matchers | Whole modules a role cannot touch |
| Method | `@PreAuthorize` on controllers | Per-operation role requirements |
| Entity | Service-layer checks | Who may act on *this* record |

The third is where the real rules live, because a role check cannot express
"only the approver assigned to this task may decide it". A user may hold several
roles; separation of duties is still enforced against their identity, not their
role set, so one account holding both MAKER and APPROVER is refused on its own
requests exactly as two accounts would be.

---

## Separation of duties

| Rule | Where | What happens if attempted |
|---|---|---|
| A Maker cannot choose an approver | `CreateBankChangeRequestCommand`, `ApproverAssignmentService` | 400 `UNEXPECTED_FIELD` |
| A Maker cannot approve their own request | `ApprovalService.validateDecisionEligibility` | 403 `MAKER_CANNOT_APPROVE`, audited |
| Only the assigned approver may decide | same | 403 `NOT_ASSIGNED_APPROVER`, audited |
| The two approvals must be different people | `ApproverAssignmentService.assignSecondApprover` | The first approver is excluded from selection; a direct attempt is 403 |
| A Maker cannot be reassigned in | `ApproverAssignmentService.reassign` | 403 `REPLACEMENT_IS_MAKER` |
| An override requester cannot decide it | `EmergencyOverrideService.decide` | 403 `OVERRIDE_SELF_APPROVAL`, audited |
| Only Accounts Payable releases a hold | `PaymentHoldService.releaseHold` | 403 `NOT_ACCOUNTS_PAYABLE` |
| Only an Administrator decides an override | `EmergencyOverrideController` + service | 403 |
| Only an Administrator reads the audit log | `AuditController` | 403 |

Blocked separation-of-duties attempts are written to the audit log as
`SEPARATION_OF_DUTIES_VIOLATION_BLOCKED`. A refusal is a signal, and signals
belong in the trail.

---

## Trusting nothing from the client

The Maker form has nine controls, and six of them reach the server. Everything
that decides the workflow's shape — who approves, what the reference is, what
state the request is in — is produced by the backend.

Command DTOs enforce this at deserialisation. A property that is not one of the
permitted fields raises `UnexpectedFieldException`, which becomes a 400. The
alternative — ignoring unknown fields — would let a request that tried to pick
its own approver appear to succeed, and nobody would know it had tried.

The same holds for payments: `paymentReference`, `id`, `status` and
`vendorBankAccountId` are all rejected if sent.

---

## Data protection

**Masking.** Account references are masked wherever they leave the service
layer: on the Maker form, in request responses, in payment responses, and in
audit reasons. The unmasked value exists in the database and nowhere else a user
can reach.

**Synthetic data only.** Every account reference in this project is fictional.

**Error responses** carry a stable code and a safe message. No stack trace, SQL
fragment, entity internal or constraint name ever reaches a client:
`include-message: never` and `include-stacktrace: never` are set, and
`GlobalExceptionHandler` translates everything.

**Secrets** are read from environment variables. `application-local.yml` is
git-ignored, and the committed example file contains placeholders only.

---

## Concurrency as a control

Two people clicking at once is an ordinary event, and in a fraud-control system
a race is a vulnerability. The guards are layered:

| Mechanism | Protects |
|---|---|
| `PESSIMISTIC_WRITE` row locks | Approval decisions, request transitions, payment processing, reference generation |
| `@Version` optimistic locking | Every mutable entity |
| Unique indexes on marker columns | One active bank record, one open request, one pending task per stage, one active hold |

A duplicate decision is a 409, not a silent second approval. Two simultaneous
requests for one vendor produce one request and one rejection. Concurrent
scheduler passes activate a request exactly once.

---

## Audit

Append-only. `AuditLog` has no setter and no update method; `AuditController`
exposes only `GET`. Business audit events join the caller's transaction, so an
audit row never survives a rolled-back operation. Security events such as a
failed login use `REQUIRES_NEW`, because they must persist exactly when the
surrounding operation fails.

Recorded: logins and login failures, request creation, every assignment and
reassignment, every approval decision, cooling-off start and completion,
activation and blocked activation, payment creation and processing, hold
placement and release, override requests and decisions, deactivations, and every
blocked separation-of-duties attempt.

---

## Known limitations

Worth stating plainly, because a control system that oversells itself is worse
than one that does not:

- **No token revocation list.** A compromised token stays valid until it
  expires, unless the user is deactivated — which `ActiveUserGuard` does catch
  immediately. An 8-hour TTL is the exposure window.
- **No refresh tokens.** Sessions end at expiry and the user signs in again.
- **No rate limiting on login.** Brute-force protection belongs at the gateway
  in this design, and is not implemented here.
- **No MFA.** For a control of this sensitivity, step-up authentication on
  approval decisions would be the obvious next control.
- **Tokens are stored in `localStorage`**, which is readable by any script that
  achieves XSS. A `HttpOnly` cookie with CSRF protection would be stronger; the
  current approach keeps the development setup simple and is the tradeoff a
  production deployment should revisit first.
- **Evidence references are strings.** The system records that evidence was
  cited, not that it exists. Integrating a document store would close that gap.
