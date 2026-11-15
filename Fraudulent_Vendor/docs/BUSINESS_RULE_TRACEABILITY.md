# Business-rule traceability

BR-001 to BR-100 against the implementation, with the test that proves each one.

**Interpretation note.** The original checklist was written against a
permission-based authorization model. This implementation is roles-only: four
roles, no `PERMISSIONS` table, no `USER_PERMISSIONS` table, no permission
checks. Rules that named a permission have been translated to the role that
holds it, and the translation is stated in the row. BR-091 to BR-100 cover the
additions this design required: backend-generated payment references, soft
delete, and the exact field list on each form.

**Verification key.** *Automated* — a named test asserts it. *Code-verified* —
confirmed by reading the implementation; no isolated test, usually because the
rule is an absence (a field that does not exist, an endpoint that is not
mapped). Nothing is marked complete on the strength of intention.

Test classes referenced:

| Short name | Class |
|---|---|
| `Auth` | `integration/AuthenticationIntegrationTest` |
| `Workflow` | `integration/BankChangeWorkflowIntegrationTest` |
| `Negative` | `integration/NegativeControlsIntegrationTest` |
| `Payment` | `integration/PaymentControlsIntegrationTest` |
| `Override` | `integration/EmergencyOverrideIntegrationTest` |
| `Reassign` | `integration/ReassignmentIntegrationTest` |
| `Scheduler` | `integration/SchedulerRecoveryIntegrationTest` |
| `Concurrency` | `integration/ConcurrencyIntegrationTest` |
| `Assignment` | `unit/ApproverAssignmentServiceTest` |
| `States` | `unit/BankChangeStatusTransitionTest` |
| `Fields` | `unit/CommandFieldRejectionTest` |
| `Masking` | `unit/MaskingUtilsTest` |
| `FE-Form` | `frontend/tests/makerForm.test.tsx` |
| `FE-Nav` | `frontend/tests/roleNavigation.test.tsx` |
| `FE-Api` | `frontend/tests/apiClient.test.ts` |

---

## Authentication and identity (BR-001 to BR-010)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-001 | A user authenticates with username and password | `AuthService.login` | `LoginPage` | `Auth.loginSucceeds` | Automated |
| BR-002 | Passwords are stored as BCrypt hashes, never plaintext | `SecurityConfig.passwordEncoder` (strength 12) | — | `Auth.wrongPasswordRejected` | Automated |
| BR-003 | A successful login returns a signed JWT carrying the user's roles | `JwtService.issueToken` | `AuthContext.login` | `Auth.loginSucceeds` | Automated |
| BR-004 | An invalid credential is rejected without revealing which part was wrong | `AuthService.login` | `LoginPage` error banner | `Auth.wrongPasswordRejected` | Automated |
| BR-005 | A deactivated (soft-deleted) user cannot sign in | `AuthService.login` | — | `Auth.inactiveUserCannotLogIn` | Automated |
| BR-006 | A deactivated user's existing token stops working immediately | `ActiveUserGuard.isStillActive` | 401 interceptor clears session | Code-verified | Code-verified |
| BR-007 | Protected endpoints reject absent or forged tokens | `JwtAuthenticationFilter`, `SecurityConfig` | `RequireRole` | `Auth.protectedEndpointsRequireAValidToken` | Automated |
| BR-008 | There is no public registration endpoint | No such mapping exists | No such screen | `Auth.noRegistrationEndpointExists` | Automated |
| BR-009 | Users are created only by a controlled bootstrap | `DevDataBootstrap`, disabled by default | — | Code-verified | Code-verified |
| BR-010 | No endpoint allows self-assignment of a role | No such mapping exists | No such screen | Code-verified | Code-verified |

## Roles and authorization (BR-011 to BR-020)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-011 | Exactly four business roles exist | `RoleName`, `V2__reference_data.sql` | `RoleName` type | Code-verified | Code-verified |
| BR-012 | *(was: permission tables)* No permissions table or permission check exists | Schema has no such table | — | Code-verified | Code-verified |
| BR-013 | A user may hold several roles | `users` ↔ `user_roles` ↔ `roles` | `AuthContext.hasAnyRole` | `Negative.makerCannotSelfApprove` (dual-role user) | Automated |
| BR-014 | Separation of duties is enforced against identity, not role set | `ApprovalService.validateDecisionEligibility` | — | `Negative.makerCannotSelfApprove` | Automated |
| BR-015 | *(was: payment permission)* Only ACCOUNTS_PAYABLE may create or process payments | `@PreAuthorize`, `PaymentService.requireAccountsPayable` | AP dashboard only | `Payment.*`, `Negative.makerIsLockedOutOfOtherModules` | Automated |
| BR-016 | *(was: audit permission)* Only ADMINISTRATOR may read audit | `AuditController` class-level `@PreAuthorize` | Admin dashboard only | `Negative.auditIsAdministratorOnly` | Automated |
| BR-017 | *(was: override permission)* Only ADMINISTRATOR decides overrides | `EmergencyOverrideService.decide` | Admin dashboard only | `Override.onlyAdministratorMayDecide` | Automated |
| BR-018 | *(was: hold permission)* Only ACCOUNTS_PAYABLE releases holds | `PaymentHoldService.releaseHold` | AP dashboard only | `Payment.holdReleaseIsRestrictedAndJustified` | Automated |
| BR-019 | The Administrator does not perform ordinary user management | No user-management endpoint or screen | No such dashboard | Code-verified | Code-verified |
| BR-020 | Route guards are convenience only; the server re-checks everything | `@PreAuthorize` + service checks | `RequireRole` | `FE-Nav`, `Negative.*` | Automated |

## Maker module (BR-021 to BR-032)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-021 | A Maker can view active vendors | `VendorService.searchActiveVendors` | `CreateBankChangeForm` dropdown | `Negative.softDeletedVendorCannotBeUsed` | Automated |
| BR-022 | A Maker can create a bank-change request | `BankChangeRequestService.create` | `CreateBankChangeForm` | `Workflow.creationDoesNotTouchLiveBankingDetails` | Automated |
| BR-023 | A Maker can view their own requests | `listMyRequests` | `MakerDashboard` | `Workflow.makerCanTrackOwnRequests` | Automated |
| BR-024 | A Maker can track approval stages and cooling-off | `BankChangeQueryService` | `CountdownBadge` | `Workflow.makerCanTrackOwnRequests` | Automated |
| BR-025 | A Maker cannot choose the first approver | `CreateBankChangeRequestCommand` rejects the field | No such control | `Negative.makerCannotSelectApprover`, `Fields`, `FE-Form` | Automated |
| BR-026 | A Maker cannot choose the second approver | same | No such control | `Negative.makerCannotSupplyServerControlledFields` | Automated |
| BR-027 | A Maker cannot submit a maker id | same | Derived from token | `Negative.makerCannotSupplyServerControlledFields` | Automated |
| BR-028 | A Maker cannot supply a request reference | same | No such control | `Negative.makerCannotSupplyServerControlledFields` | Automated |
| BR-029 | A Maker cannot select an approval stage or status | same | No such control | `Fields.rejectsOtherServerControlledFields` | Automated |
| BR-030 | A Maker cannot approve their own request | `ApprovalService` | — | `Negative.makerCannotSelfApprove` | Automated |
| BR-031 | A Maker cannot reassign an approval task | `@PreAuthorize` on reassign | No such control | `Reassign.reassignmentIsRoleRestricted` | Automated |
| BR-032 | A Maker cannot release a payment hold or activate details | `@PreAuthorize`, service checks | No such control | `Negative.makerIsLockedOutOfOtherModules` | Automated |

## Create Bank-Change Request form (BR-033 to BR-042)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-033 | The form shows a searchable active-vendor dropdown | `GET /vendors?search=` | `CreateBankChangeForm` | `FE-Form` | Automated |
| BR-034 | The form shows the current bank reference, masked and read-only | `getCurrentBankAccount`, `MaskingUtils` | `disabled` input | `FE-Form.showsTheCurrentBankReferenceMaskedAndReadOnly` | Automated |
| BR-035 | The form collects the proposed account holder name | `proposedAccountHolderName` | field | `FE-Form` | Automated |
| BR-036 | The form collects the proposed bank name | `proposedBankName` | field | `FE-Form` | Automated |
| BR-037 | The form collects the proposed synthetic account reference | `proposedAccountReference` | field | `FE-Form.rejectsAnAccountReference...` | Automated |
| BR-038 | The form collects the routing code | `proposedRoutingCode` | field | `FE-Form` | Automated |
| BR-039 | The form collects a reason for the change | `reason`, min 10 characters | field | `FE-Form.refusesToSubmitWithout...` | Automated |
| BR-040 | The form collects an evidence reference | `evidenceReference` | field | `FE-Form` | Automated |
| BR-041 | No approver selection field appears anywhere in the Maker workflow | — | asserted absent | `FE-Form.offersNoApproverReferenceOrStatusField` | Automated |
| BR-042 | The backend rejects unexpected approver, maker, reference or status fields | `@JsonAnySetter` → 400 | — | `Fields`, `Negative.*` | Automated |

## Automatic approver assignment (BR-043 to BR-055)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-043 | Only active APPROVER users are eligible | `UserRepository.findActiveByRole` | — | `Assignment.inactiveApproversAreExcluded` | Automated |
| BR-044 | The Maker is excluded from selection | `assignFirstApprover` | — | `Assignment.makerIsNeverEligible` | Automated |
| BR-045 | Selection is deterministic least-pending-task | `selectApprover` | — | `Assignment.choosesLeastLoadedApprover` | Automated |
| BR-046 | Ties break stably on the lowest user id | `Comparator.thenComparingLong` | — | `Assignment.tieBreaksDeterministically` | Automated |
| BR-047 | The first approver is assigned at creation | `BankChangeRequestService.create` | — | `Workflow.firstApproverAssignedAutomatically` | Automated |
| BR-048 | The second approver is assigned only after first-stage approval | `assignSecondApprover` | — | `Assignment.secondStageRequiresFirstApproval` | Automated |
| BR-049 | The second approver differs from the first and from the Maker | exclusion set from `findAllInvolvedAssigneeIds` | — | `Assignment.secondApproverIsIndependent`, `Workflow.secondApproverIsADifferentPerson` | Automated |
| BR-050 | The same person can never approve both stages | assignment exclusion + explicit check | — | `Negative.sameApproverCannotApproveBothStages` | Automated |
| BR-051 | Only the assigned approver may decide | `validateDecisionEligibility` | — | `Negative.onlyAssignedApproverMayDecide` | Automated |
| BR-052 | Unavailable approvers do not bypass the workflow | `NO_ELIGIBLE_APPROVER`; status unchanged | message in reassign dialog | `Assignment.noEligibleApproverStopsTheWorkflow` | Automated |
| BR-053 | Assignment and reassignment history is preserved | `approval_assignment_history`, `REASSIGNED` status | trail on detail page | `Reassign.accountsPayableMayReassign` | Automated |
| BR-054 | Every assignment generates a notification | `NotificationService.notifyUser` | bell | `Assignment.assignmentIsAuditedAndNotified` | Automated |
| BR-055 | Duplicate assignments are prevented | `guardNoPendingTask` + `uk_at_one_open` | — | `Concurrency.concurrentDecisionsProduceOneWinner` | Automated |

## Approval module (BR-056 to BR-066)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-056 | The assigned approver reviews the full request | `GET /bank-change-requests/{id}` | review dialog | `Workflow` | Automated |
| BR-057 | An approver may approve or reject | `ApprovalService.decide` | Approve/Reject | `Workflow`, `Negative` | Automated |
| BR-058 | A rejection requires a meaningful comment | min 10 characters | client check mirrors it | `Negative.rejectionRequiresComment` | Automated |
| BR-059 | Each decision records a timestamp and an audit event | `decidedAt`, `AuditService` | shown in trail | `Workflow.auditTrailCoversTheRun` | Automated |
| BR-060 | A first approval assigns the second approver | `handleFirstApproval` | — | `Workflow.secondApproverIsADifferentPerson` | Automated |
| BR-061 | The second stage is available only after the first | status gate + task creation order | — | `Negative.outOfOrderApprovalIsRejected` | Automated |
| BR-062 | The first approver cannot approve again | explicit check | — | `Negative.sameApproverCannotApproveBothStages` | Automated |
| BR-063 | A rejection terminates normal progression | `markRejected`, terminal state | status badge | `Workflow.rejectionStopsTheWorkflow` | Automated |
| BR-064 | A successful second approval begins cooling-off | `startCoolingOff` | countdown | `Workflow.secondApprovalStartsCoolingOff` | Automated |
| BR-065 | Proposed details remain inactive until every control passes | activation only in `BankActivationService` | — | `Workflow.secondApprovalStartsCoolingOff` | Automated |
| BR-066 | Completed decisions are never modified or deleted | `requirePending` guard; no update endpoint | — | `Negative.duplicateDecisionIsRejected` | Automated |

## Cooling-off (BR-067 to BR-074)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-067 | The default demonstration period is 24 hours | `CoolingOffProperties` default | — | Code-verified | Code-verified |
| BR-068 | The duration is configurable | `app.cooling-off.duration` | — | Code-verified | Code-verified |
| BR-069 | Start and end timestamps are stored | `cooling_off_start_at/_end_at` | countdown | `Workflow.secondApprovalStartsCoolingOff` | Automated |
| BR-070 | Spring Scheduler monitors the deadline | `CoolingOffScheduler` | — | `Workflow.schedulerActivatesAfterDeadline...` | Automated |
| BR-071 | Processing recovers correctly after restart | deadline is database state | — | `Scheduler.deadlineMissedWhileDownIsRecovered` | Automated |
| BR-072 | Scheduled processing is idempotent | row lock + status guard | — | `Scheduler.repeatedPassesAreIdempotent`, `Concurrency.concurrentSchedulerPasses...` | Automated |
| BR-073 | Early activation is prevented | `isCoolingOffElapsed` revalidation | no control exists | `Workflow.schedulerDoesNotActivateEarly` | Automated |
| BR-074 | A read-only countdown is displayed | `CoolingOffView` | `CountdownBadge` | `FE-Form` / `formatting` | Automated |

## Payments (BR-075 to BR-084)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-075 | All payment references are backend-generated | `ReferenceGeneratorService` | no control | `Payment.backendGeneratesPaymentReference` | Automated |
| BR-076 | Reference generation is concurrency-safe and never `MAX(id)+1` | row-locked `reference_sequences` + unique index | — | `Concurrency.concurrentReferenceGenerationIsUnique` | Automated |
| BR-077 | The payment form shows only the five business fields | `CreatePaymentCommand` | `CreatePaymentForm` | `Payment.clientSuppliedReferenceRejected`, `Fields` | Automated |
| BR-078 | Payment creation is restricted to ACCOUNTS_PAYABLE | `@PreAuthorize` + service check | AP dashboard | `Negative.makerIsLockedOutOfOtherModules` | Automated |
| BR-079 | Holds are created during pending bank-change workflows | `placeHoldsForPendingBankChange` | hold list | `Payment.bankChangeRequestHoldsExistingPayments` | Automated |
| BR-080 | An active hold blocks payment processing | `evaluateEligibility` | disabled action | `Payment.heldPaymentCannotBeProcessed` | Automated |
| BR-081 | Eligibility is validated on every payment operation | recomputed, never cached | live badge | `Payment.openRequestKeepsPaymentIneligible` | Automated |
| BR-082 | Hold reason and status are stored | `payment_holds` | shown | `Payment.*` | Automated |
| BR-083 | Release is separate, AP-only, justified, rechecked and audited | `releaseHold` | release dialog | `Payment.holdReleaseIsRestrictedAndJustified` | Automated |
| BR-084 | Holds are never released automatically by an override | nothing calls release except the endpoint | — | `Override.approvedOverrideDoesNotReleaseHolds` | Automated |

## Emergency override and reassignment (BR-085 to BR-090)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-085 | Only ACCOUNTS_PAYABLE requests an override | `@PreAuthorize` + service check | AP dashboard | `Override.onlyAccountsPayableMayRequest` | Automated |
| BR-086 | Only ADMINISTRATOR decides an override; never the requester | service checks both | Admin dashboard | `Override.onlyAdministratorMayDecide`, `.requesterCannotSelfApprove` | Automated |
| BR-087 | An override requires completed dual approval | `requireCompletedDualApproval` | — | `Override.overrideRequiresCompletedDualApproval` | Automated |
| BR-088 | An override authorises a cooling-off exception only | `applyCoolingOffException` | explained in dialog | `Override.approvedOverrideOnlyShortensCoolingOff` | Automated |
| BR-089 | Reassignment is AP/ADMIN only, with a mandatory reason | `@PreAuthorize`, `reassign` | reassign dialog | `Reassign.reassignmentIsRoleRestricted`, `.reasonIsMandatory` | Automated |
| BR-090 | A replacement must be active, an APPROVER, not the Maker, not already involved | `reassign` validations | filtered candidate list | `Reassign.replacementMustBeAnActiveApprover`, `.makerCannotBeReassignedIn`, `.cannotReassignToSomeoneAlreadyInvolved` | Automated |

## Notifications, audit, soft delete, data integrity (BR-091 to BR-100)

| ID | Rule (final interpretation) | Backend | Frontend | Test | Status |
|---|---|---|---|---|---|
| BR-091 | Notifications are database-backed and cover every workflow event | `NotificationService`, `NotificationType` | bell and list | `Assignment.assignmentIsAuditedAndNotified` | Automated |
| BR-092 | Only the recipient may read or update their notifications | repository scoped by recipient | — | `Negative.notificationsArePrivate` | Automated |
| BR-093 | Notification delivery is transactionally reliable | `Propagation.MANDATORY` | — | Code-verified | Code-verified |
| BR-094 | Audit records are append-only with actor, action, entity, timestamp, reason, state | `AuditLog`, no setters | admin table | `Workflow.auditTrailCoversTheRun` | Automated |
| BR-095 | No ordinary audit update or delete endpoint exists | only `GET` is mapped | read-only table | `Negative.auditHasNoWriteEndpoints` | Automated |
| BR-096 | Sensitive references are masked wherever they leave the service layer | `MaskingUtils`, `AuditQueryService.mask` | masked everywhere | `Masking`, `Workflow` | Automated |
| BR-097 | Soft delete applies to users, vendors and vendor bank accounts | `is_active`, `deleted_at`, `deleted_by` | — | `Auth.inactiveUserCannotLogIn` | Automated |
| BR-098 | Inactive or soft-deleted vendors never appear in active dropdowns | `VendorRepository.searchActive` | dropdown | `Negative.softDeletedVendorCannotBeUsed` | Automated |
| BR-099 | Deactivation is prevented while unresolved work makes it unsafe | `VendorService.deactivateVendor` | — | Code-verified | Code-verified |
| BR-100 | Completed approvals, audit history, payments and override decisions are never soft-deleted | lifecycle statuses only; no delete path | — | Code-verified | Code-verified |

---

## Additional rules this design required

| ID | Rule | Backend | Test | Status |
|---|---|---|---|---|
| BR-A01 | Bank-change references are backend-generated and unique | `ReferenceGeneratorService` + `uk_bcr_reference` | `Workflow.creationDoesNotTouchLiveBankingDetails` | Automated |
| BR-A02 | Override references are backend-generated and unique | same pattern | `Override.onlyAccountsPayableMayRequest` | Automated |
| BR-A03 | One open bank-change request per vendor | service guard + `uk_bcr_one_open_per_vendor` | `Negative.duplicateOpenRequestIsRefused`, `Concurrency.concurrentDuplicateRequests...` | Automated |
| BR-A04 | A request proposing no actual change is refused | `NO_CHANGE_PROPOSED` | `Negative.noChangeProposedIsRefused` | Automated |
| BR-A05 | Activation supersedes rather than overwrites; history is preserved | `VendorBankAccount.supersede` | `Workflow.schedulerActivatesAfterDeadlineAndPreservesHistory` | Automated |
| BR-A06 | Failed revalidation blocks activation rather than forcing it through | `ACTIVATION_BLOCKED` | `Scheduler.vendorDeactivatedDuringWindowBlocksActivation` | Automated |
| BR-A07 | A blocked request is not silently activated by a later pass | status guard | `Scheduler.blockedRequestIsNotSilentlyActivatedLater` | Automated |
| BR-A08 | A rejection does not release payment holds | nothing calls release | `Payment.rejectionDoesNotReleaseHolds` | Automated |
| BR-A09 | Activation does not release payment holds | nothing calls release | `Payment.activationDoesNotReleaseHolds` | Automated |
| BR-A10 | A payment raised during an open bank change is held immediately | `placeHoldForNewPayment` | `Payment.newPaymentDuringOpenRequestIsHeld` | Automated |
| BR-A11 | Workflow state transitions are explicit and invalid ones are refused | `BankChangeStatus.canTransitionTo` | `States` (11 tests) | Automated |
| BR-A12 | API errors carry a stable business code, never an internal detail | `GlobalExceptionHandler` | `FE-Api`, every negative test | Automated |
