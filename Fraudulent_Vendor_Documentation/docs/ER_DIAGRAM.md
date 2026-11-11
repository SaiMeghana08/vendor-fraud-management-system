# Entity relationships

![ER diagram](diagrams/er-diagram.svg)

Mermaid source: [`diagrams/er-diagram.mmd`](diagrams/er-diagram.mmd) ·
Rendered: [`er-diagram.svg`](diagrams/er-diagram.svg),
[`er-diagram.png`](diagrams/er-diagram.png)

The diagram matches `V1__initial_schema.sql` exactly. There is no permissions
table, no user-permissions table, and no permission column anywhere:
authorization is role-based.

---

## The fourteen tables

| Table | Holds | Soft delete |
|---|---|---|
| `users` | Identities and BCrypt password hashes | Yes |
| `roles` | The four business roles | No (reference data) |
| `user_roles` | Many-to-many; multiple roles per user supported | No |
| `vendors` | Vendor master | Yes |
| `vendor_bank_accounts` | Current and superseded banking records | Yes |
| `bank_change_requests` | The workflow instance | No — lifecycle status |
| `approval_tasks` | One row per stage per assignment | No — lifecycle status |
| `approval_assignment_history` | Append-only assignment trail | No |
| `payments` | Vendor payments | No — lifecycle status |
| `payment_holds` | Blocks on specific payments | No — lifecycle status |
| `emergency_overrides` | Cooling-off exception requests and decisions | No |
| `notifications` | Private per-recipient messages | No |
| `audit_logs` | Append-only audit trail | No |
| `reference_sequences` | Backs reference generation | No |

---

## Design decisions worth explaining

### `active_marker` and `open_marker`

MySQL has no partial unique index, so these columns emulate one. The column
holds `1` while a row is active or open and `NULL` once it is not, and a unique
index permits many `NULL`s. That gives four guarantees at the storage layer
rather than merely in application code:

| Constraint | Guarantees |
|---|---|
| `uk_vba_one_active (vendor_id, active_marker)` | One active banking record per vendor |
| `uk_bcr_one_open_per_vendor (vendor_id, open_marker)` | One open bank-change request per vendor |
| `uk_at_one_open (bank_change_request_id, stage, open_marker)` | One pending task per request and stage |
| `uk_ph_one_active (payment_id, bank_change_request_id, active_marker)` | One active hold per payment and request |

The service layer checks these conditions first and returns a readable error.
The index is what holds when two requests race.

### Soft delete where it belongs

Master records (`users`, `vendors`, `vendor_bank_accounts`) carry `is_active`,
`deleted_at` and `deleted_by`. Deactivating a user preserves every approval they
ever made and every foreign key pointing at them.

Transactional records do not get soft delete; they get lifecycle statuses. A
completed approval, an audit row, a payment and an override decision are
historical facts, and the schema offers no way to retract one.

### History relationships

`vendor_bank_accounts.source_request_id` records which bank-change request
activated a given record, so any live banking detail can be traced to the
request, approvals and evidence that produced it. The superseded row keeps its
`superseded_at` timestamp and is never deleted.

### `reference_sequences`

One row per reference family. Generation takes a pessimistic write lock on the
row, reads `next_value`, increments, and commits in its own short transaction —
never `MAX(id)+1`, which cannot be made safe under concurrency. Gaps are
acceptable; references are identifiers, not a count. The unique constraints on
`request_reference`, `payment_reference` and `override_reference` are the final
guarantee.
