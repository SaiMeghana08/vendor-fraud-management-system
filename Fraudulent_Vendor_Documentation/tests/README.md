# Acceptance scenarios

The automated suites live with the code they test:

| Suite | Location | Count |
|---|---|---|
| Backend unit and integration | `backend/src/test/java/com/vendorfraud/` | 98 |
| Frontend component and unit | `frontend/tests/` | 15 |

This folder holds the manual walkthroughs — the things worth doing by hand once
the system is running, because they are about watching the controls behave
rather than asserting on a response.

Run them with `COOLING_OFF_DURATION=PT2M` so the window closes while you watch.

---

## A1 — The happy path, end to end

1. Sign in as `maker.one`. Raise a request for Northwind Industrial Supplies.
2. Note the reference in the response, and that the form never asked you to
   choose an approver.
3. Check the vendor's banking record in MySQL. It is unchanged.
4. Sign in as the approver named on the request. Approve it.
5. Observe that a *different* approver now holds the second stage.
6. Sign in as that second approver. Approve.
7. Watch the countdown on the Maker dashboard.
8. Wait for the scheduler. The request becomes `ACTIVATED`.
9. In MySQL, confirm two rows in `vendor_bank_accounts` for that vendor: one
   `ACTIVE`, one `SUPERSEDED` with a `superseded_at` timestamp.

**What this demonstrates.** Dual approval, independence, inertness until every
control passes, and history preservation.

---

## A2 — Payments stop, and resume only on purpose

1. As `ap.one`, create a payment for a vendor. It is eligible.
2. As `maker.one`, raise a bank-change request for that same vendor.
3. Back in the Accounts Payable dashboard: the payment is `ON_HOLD` and
   processing is refused.
4. Take the request all the way to `ACTIVATED`.
5. The payment is **still held**. Activation did not release it.
6. Release the hold with a justification. Only now is the payment payable.

**What this demonstrates.** Holds are independent of the approval outcome, and
resuming payment is its own decision.

---

## A3 — The override is narrower than it sounds

1. Take a request to `COOLING_OFF`.
2. As `ap.one`, request an emergency override with a justification.
3. As `admin.one`, approve it.
4. The request is **still** `COOLING_OFF` — the deadline moved, the status did
   not.
5. The scheduler runs, revalidates, and activates.
6. Any payment hold on that vendor is still active.

**What this demonstrates.** An approved override authorises a cooling-off
exception and nothing else.

---

## A4 — Separation of duties against one account

1. Create a user holding both MAKER and APPROVER (via the bootstrap).
2. Raise a request as that user.
3. Observe that the assignment skipped them entirely.
4. Try to approve it anyway through the API: 403 `MAKER_CANNOT_APPROVE`.
5. As `admin.one`, check the audit log: the attempt is recorded as
   `SEPARATION_OF_DUTIES_VIOLATION_BLOCKED`.

**What this demonstrates.** Separation of duties is enforced against identity,
not against a role set.

---

## A5 — An approver disappears

1. Take a request to `PENDING_FIRST_APPROVAL`.
2. As `ap.one`, open the pending tasks list and reassign the task, with a
   reason.
3. Note the candidate list: the Maker is not in it.
4. On the request detail page, the old task shows `REASSIGNED` and a new
   `PENDING` task sits below it.
5. The replacement approves. The workflow continues normally.

**What this demonstrates.** Reassignment preserves history and cannot be used to
weaken independence.

---

## A6 — Restart recovery

1. Take a request to `COOLING_OFF`.
2. Stop the backend before the window closes.
3. Wait past the deadline.
4. Start the backend again.
5. Within one scheduler interval, the request activates.

**What this demonstrates.** The deadline is database state, so downtime delays
activation rather than losing it.

---

## A7 — Notification privacy

1. Sign in as the approver holding a task. Note a notification id.
2. Sign in as a different approver. The notification is absent from their list,
   and `POST /api/notifications/{id}/read` returns 404.

**What this demonstrates.** Notifications are scoped to their recipient at the
repository level, not filtered in the UI.
