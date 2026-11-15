# API contract

Base URL: `http://localhost:9090/api`
Authentication: `Authorization: Bearer <token>` on everything except
`POST /auth/login` and `GET /health`.

Every response body is JSON. Every failure uses one shape.

---

## Error shape

```json
{
  "timestamp": "2026-11-11T10:04:22.481Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "DUPLICATE_OPEN_REQUEST",
  "message": "An open bank-change request already exists for this vendor",
  "path": "/api/bank-change-requests",
  "fieldErrors": [
    { "field": "reason", "message": "Please give a meaningful reason (at least 10 characters)" }
  ]
}
```

`fieldErrors` appears only for validation failures. `code` is stable and
machine-readable; `message` is safe to display.

### Status codes

| Status | Meaning here |
|---|---|
| 400 | Malformed body, failed validation, or a client-supplied server-controlled field |
| 401 | Missing, expired or invalid token; failed login |
| 403 | Authenticated but not permitted — role, separation of duties, or ownership |
| 404 | No such entity, or not visible to this caller |
| 405 | The operation does not exist on this resource |
| 409 | Out-of-order or repeated operation; concurrent modification |
| 422 | Well-formed but refused by a business rule |
| 500 | Unexpected failure. No stack trace or SQL ever reaches the client |

### Business codes

| Code | Status | Raised when |
|---|---|---|
| `UNEXPECTED_FIELD` | 400 | A client sent an approver id, maker id, reference or status |
| `VALIDATION_FAILED` | 400 | Bean Validation rejected one or more fields |
| `UNAUTHENTICATED` | 401 | No valid token |
| `ACCESS_DENIED` | 403 | Role check failed |
| `MAKER_CANNOT_APPROVE` | 403 | A Maker tried to approve their own request |
| `NOT_ASSIGNED_APPROVER` | 403 | An approver tried to decide someone else's task |
| `SAME_APPROVER_BOTH_STAGES` | 403 | The first approver tried to take the second stage |
| `OVERRIDE_SELF_APPROVAL` | 403 | The override requester tried to decide it |
| `REPLACEMENT_IS_MAKER` | 403 | Reassignment aimed at the Maker |
| `REPLACEMENT_ALREADY_INVOLVED` | 403 | Reassignment aimed at someone already on the request |
| `NOT_A_MAKER` / `NOT_AN_APPROVER` / `NOT_ACCOUNTS_PAYABLE` / `NOT_ADMINISTRATOR` | 403 | Role required for this operation |
| `NOT_FOUND` | 404 | No such entity, or not visible |
| `TASK_ALREADY_DECIDED` | 409 | A decided task was decided again |
| `OUT_OF_ORDER_APPROVAL` | 409 | A stage decision that does not match the request's state |
| `HOLD_ALREADY_RELEASED` | 409 | A released hold was released again |
| `OVERRIDE_ALREADY_DECIDED` | 409 | A decided override was decided again |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic lock failure |
| `CONSTRAINT_VIOLATION` | 409 | A database constraint refused the write |
| `DUPLICATE_OPEN_REQUEST` | 422 | A vendor already has an open request |
| `NO_CHANGE_PROPOSED` | 422 | Proposed details match the current ones |
| `NO_ELIGIBLE_APPROVER` | 422 | No independent approver is available |
| `REJECTION_COMMENT_REQUIRED` | 422 | A rejection arrived without a meaningful comment |
| `REASSIGNMENT_REASON_REQUIRED` | 422 | A reassignment arrived without a reason |
| `REPLACEMENT_INACTIVE` / `REPLACEMENT_NOT_APPROVER` / `TASK_NOT_PENDING` | 422 | Reassignment preconditions failed |
| `PAYMENT_NOT_ELIGIBLE` | 422 | Processing blocked by a hold, an open request, or state |
| `VENDOR_HAS_NO_ACTIVE_BANK_ACCOUNT` | 422 | Nothing to pay against |
| `RELEASE_JUSTIFICATION_REQUIRED` | 422 | A hold release arrived without a justification |
| `DUAL_APPROVAL_INCOMPLETE` | 422 | An override was requested before both approvals |
| `OVERRIDE_NOT_APPLICABLE` | 422 | An override aimed at a request not in cooling-off |
| `DUPLICATE_OVERRIDE` | 422 | A second pending override for one request |
| `VENDOR_HAS_UNRESOLVED_WORK` | 422 | Deactivation attempted with work in flight |

---

## Authentication

### `POST /auth/login` — public

```json
{ "username": "maker.one", "password": "..." }
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-11-11T18:04:22Z",
  "user": { "id": 3, "username": "maker.one", "fullName": "Maya Kerr",
            "email": "maker.one@vendorfraud.example", "active": true, "roles": ["MAKER"] },
  "roles": ["ROLE_MAKER"]
}
```

A wrong password and a deactivated account both return `401` with the same
message. The audit trail records them differently.

### `GET /auth/me`

The caller's own profile, derived from the token.

**There is no `POST /auth/register`** and no endpoint that grants a role.

---

## Vendors

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/vendors?search=` | any | Active vendors only; feeds the dropdown |
| GET | `/vendors/{id}` | any | 404 if inactive or soft-deleted |
| GET | `/vendors/{id}/current-bank-account` | any | Masked; `null` if no record |
| GET | `/vendors/{id}/bank-account-history` | APPROVER, AP, ADMIN | Includes superseded records |

Account references are always masked: `SYN-AC-100001` leaves as
`•••••••••0001`. The unmasked value never leaves the service layer.

---

## Bank-change requests

### `POST /bank-change-requests` — MAKER

```json
{
  "vendorId": 1,
  "proposedAccountHolderName": "Northwind Industrial Supplies",
  "proposedBankName": "Fictional Mutual Bank",
  "proposedAccountReference": "SYN-AC-200777",
  "proposedRoutingCode": "SYNRT-0002",
  "reason": "Vendor consolidated its banking to a single provider",
  "evidenceReference": "EVD-2026-0001"
}
```

Six fields. **Any other property is a 400**, including `firstApproverId`,
`secondApproverId`, `makerId`, `requestReference`, `status` and
`approvalStage`.

Response `201`:

```json
{
  "id": 1,
  "requestReference": "BCR-2026-000001",
  "vendorId": 1, "vendorName": "Northwind Industrial Supplies", "vendorCode": "VND-1001",
  "makerId": 3, "makerName": "Maya Kerr",
  "currentMaskedAccountReference": "•••••••••0001",
  "proposedMaskedAccountReference": "•••••••••0777",
  "proposedBankName": "Fictional Mutual Bank",
  "proposedRoutingCode": "SYNRT-0002",
  "reason": "Vendor consolidated its banking to a single provider",
  "evidenceReference": "EVD-2026-0001",
  "status": "PENDING_FIRST_APPROVAL",
  "coolingOff": null,
  "createdAt": "2026-11-11T10:04:22Z",
  "approvalTasks": [
    { "id": 1, "requestId": 1, "requestReference": "BCR-2026-000001",
      "stage": "FIRST", "assigneeId": 5, "assigneeName": "Priya Raman",
      "status": "PENDING", "assignedAt": "2026-11-11T10:04:22Z" }
  ]
}
```

| Method | Path | Roles | Notes |
|---|---|---|---|
| GET | `/bank-change-requests/mine` | MAKER | Own requests only |
| GET | `/bank-change-requests?status=&page=&size=` | APPROVER, AP, ADMIN | |
| GET | `/bank-change-requests/{id}` | any | A Maker gets 403 for someone else's |
| GET | `/bank-change-requests/blocked` | ADMIN, AP | Failed revalidations |

### Statuses

`PENDING_FIRST_APPROVAL` → `PENDING_SECOND_APPROVAL` → `COOLING_OFF` →
`ACTIVATED`, with `REJECTED` and `CANCELLED` terminal from any open state and
`ACTIVATION_BLOCKED` when revalidation fails.

---

## Approvals

| Method | Path | Roles |
|---|---|---|
| GET | `/approvals/my-tasks` | APPROVER |
| GET | `/approvals/my-history` | APPROVER |
| GET | `/approvals/pending` | AP, ADMIN |
| POST | `/approvals/tasks/{taskId}/decision` | APPROVER |
| GET | `/approvals/tasks/{taskId}/eligible-replacements` | AP, ADMIN |
| POST | `/approvals/tasks/{taskId}/reassign` | AP, ADMIN |

### `POST /approvals/tasks/{taskId}/decision`

```json
{ "approved": true, "comment": "Evidence verified against the vendor letterhead" }
```

The stage comes from the task and the approver from the token. A rejection needs
at least ten characters of comment. Returns the updated request, including the
newly assigned second-stage task when a first approval succeeds.

### `POST /approvals/tasks/{taskId}/reassign`

```json
{ "replacementUserId": 7, "reason": "The assigned approver is on leave until month end" }
```

The replacement must be active, hold APPROVER, not be the Maker, and not have
held any stage of this request. The old task closes as `REASSIGNED`; a new
`PENDING` task is created.

---

## Payments

| Method | Path | Roles |
|---|---|---|
| POST | `/payments` | AP |
| GET | `/payments?status=&vendorId=&page=&size=` | AP, ADMIN |
| GET | `/payments/{id}` | AP, ADMIN |
| GET | `/payments/{id}/eligibility` | AP, ADMIN |
| POST | `/payments/{id}/process` | AP |
| GET | `/payments/{id}/holds` | AP, ADMIN |

### `POST /payments`

```json
{
  "vendorId": 1,
  "amount": 12500.00,
  "currency": "USD",
  "dueDate": "2026-12-15",
  "description": "Quarterly facility services"
}
```

Five fields. `paymentReference`, `id`, `status`, `vendorBankAccountId` and the
timestamps are all generated; sending any of them is a 400.

The reference format is `PAY-YYYY-NNNNNN`, e.g. `PAY-2026-000001`.

### Eligibility

Recomputed from live state on every call and immediately before processing,
never read from a stored flag. A payment is ineligible when it is already
processed or cancelled, carries an active hold, belongs to an inactive vendor,
has no active banking record to pay against, or belongs to a vendor with an open
bank-change request.

---

## Payment holds

| Method | Path | Roles |
|---|---|---|
| GET | `/holds?page=&size=` | AP, ADMIN |
| POST | `/holds/{holdId}/release` | AP only |

```json
{ "justification": "Vendor confirmed banking details by callback to a known number" }
```

At least ten characters. Conditions are rechecked at release time. The actor,
timestamp and justification are recorded, and the release is audited. Releasing
the last active hold on a payment returns it to a payable state; while any other
hold remains, it stays held.

---

## Emergency overrides

| Method | Path | Roles |
|---|---|---|
| POST | `/overrides` | AP only |
| POST | `/overrides/decision/{overrideId}` | ADMINISTRATOR only |
| GET | `/overrides?status=&page=&size=` | AP, ADMIN |
| GET | `/overrides/{id}` | AP, ADMIN |

```json
{
  "bankChangeRequestId": 1,
  "justification": "Vendor payroll run is due today and the controller has confirmed the new details by callback",
  "evidenceReference": "EVD-OVR-2026-0001"
}
```

Requires completed dual approval and a request in `COOLING_OFF`. The requester
can never decide it. An approved override brings the cooling-off deadline
forward and does nothing else — not dual approval, not activation, not holds,
not eligibility.

---

## Notifications

| Method | Path |
|---|---|
| GET | `/notifications?page=&size=` |
| GET | `/notifications/summary` |
| POST | `/notifications/{id}/read` |

Every endpoint is scoped to the authenticated recipient. Asking for another
user's notification returns 404 — indistinguishable from one that does not
exist.

---

## Audit

| Method | Path | Roles |
|---|---|---|
| GET | `/audit?action=&entityType=&entityId=&page=&size=` | ADMINISTRATOR only |

Read-only. There is no POST, PUT, PATCH or DELETE. Account references inside
audit reasons are masked on the way out, so even an Administrator reading the
trail does not see a full banking reference.

---

## Health

`GET /health` — public. Returns `{"status":"UP", ...}`.
