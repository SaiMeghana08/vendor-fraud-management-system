/** Shapes returned by the backend. Mirrors the DTOs exactly. */

export type RoleName = 'MAKER' | 'APPROVER' | 'ACCOUNTS_PAYABLE' | 'ADMINISTRATOR';

export type Authority = `ROLE_${RoleName}`;

export interface UserProfile {
  id: number;
  username: string;
  fullName: string;
  email: string;
  active: boolean;
  roles: RoleName[];
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: UserProfile;
  roles: Authority[];
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  fieldErrors?: { field: string; message: string }[];
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Vendor {
  id: number;
  vendorCode: string;
  name: string;
  contactEmail?: string;
  active: boolean;
}

export type BankAccountStatus = 'ACTIVE' | 'SUPERSEDED';

export interface VendorBankAccount {
  id: number;
  accountHolderName: string;
  bankName: string;
  /** Always masked by the backend. */
  maskedAccountReference: string;
  routingCode: string;
  status: BankAccountStatus;
  activatedAt?: string;
  supersededAt?: string;
}

export type BankChangeStatus =
  | 'PENDING_FIRST_APPROVAL'
  | 'PENDING_SECOND_APPROVAL'
  | 'COOLING_OFF'
  | 'ACTIVATED'
  | 'REJECTED'
  | 'CANCELLED'
  | 'ACTIVATION_BLOCKED';

export type ApprovalStage = 'FIRST' | 'SECOND';

export type ApprovalTaskStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'REASSIGNED';

export interface ApprovalTask {
  id: number;
  requestId: number;
  requestReference: string;
  stage: ApprovalStage;
  assigneeId: number;
  assigneeName: string;
  status: ApprovalTaskStatus;
  decisionComment?: string;
  assignedAt: string;
  decidedAt?: string;
}

export interface CoolingOffView {
  startAt: string;
  endAt: string;
  configuredHours?: number;
  remainingSeconds: number;
  elapsed: boolean;
  exceptionApplied: boolean;
}

export interface BankChangeRequest {
  id: number;
  requestReference: string;
  vendorId: number;
  vendorName: string;
  vendorCode: string;
  makerId: number;
  makerName: string;
  currentMaskedAccountReference?: string;
  proposedAccountHolderName: string;
  proposedBankName: string;
  proposedMaskedAccountReference: string;
  proposedRoutingCode: string;
  reason: string;
  evidenceReference: string;
  status: BankChangeStatus;
  terminalReason?: string;
  coolingOff?: CoolingOffView;
  activatedAt?: string;
  createdAt: string;
  updatedAt: string;
  approvalTasks: ApprovalTask[];
}

export type PaymentStatus = 'CREATED' | 'ON_HOLD' | 'PROCESSED' | 'CANCELLED';

export type HoldStatus = 'ACTIVE' | 'RELEASED';

export interface PaymentHold {
  id: number;
  paymentId: number;
  bankChangeRequestId?: number;
  reason: string;
  status: HoldStatus;
  releaseJustification?: string;
  releasedBy?: number;
  releasedAt?: string;
  createdAt: string;
}

export interface Payment {
  id: number;
  paymentReference: string;
  vendorId: number;
  vendorName: string;
  maskedPayToAccountReference?: string;
  amount: number;
  currency: string;
  dueDate: string;
  description: string;
  status: PaymentStatus;
  eligibleForProcessing: boolean;
  ineligibilityReason?: string;
  activeHoldCount: number;
  holds: PaymentHold[];
  processedAt?: string;
  createdAt: string;
}

export interface PaymentEligibility {
  paymentId: number;
  paymentReference: string;
  eligible: boolean;
  reason?: string;
  activeHoldCount: number;
}

export type OverrideStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface EmergencyOverride {
  id: number;
  overrideReference: string;
  bankChangeRequestId: number;
  bankChangeRequestReference?: string;
  requestedBy: number;
  requestedByName?: string;
  justification: string;
  evidenceReference: string;
  status: OverrideStatus;
  decidedBy?: number;
  decidedByName?: string;
  decidedAt?: string;
  decisionComment?: string;
  createdAt: string;
}

export interface Notification {
  id: number;
  type: string;
  title: string;
  message: string;
  referenceType?: string;
  referenceId?: number;
  read: boolean;
  readAt?: string;
  createdAt: string;
}

export interface AuditLog {
  id: number;
  actorId?: number;
  actorUsername: string;
  action: string;
  entityType: string;
  entityId?: number;
  workflowState?: string;
  reason?: string;
  createdAt: string;
}

export interface ApproverOption {
  id: number;
  username: string;
  fullName: string;
}
