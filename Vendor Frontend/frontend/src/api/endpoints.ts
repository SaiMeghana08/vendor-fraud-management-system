import { api } from './client';
import type {
  ApprovalTask, ApproverOption, AuditLog, BankChangeRequest, BankChangeStatus,
  EmergencyOverride, LoginResponse, Notification, Page, Payment, PaymentEligibility,
  PaymentHold, PaymentStatus, UserProfile, Vendor, VendorBankAccount,
} from '../types';

/**
 * Every call the UI can make. There is deliberately no helper here for
 * choosing an approver, supplying a reference or setting a status - the
 * backend owns all of those and rejects them if sent.
 */

export const authApi = {
  login: (username: string, password: string) =>
    api.post<LoginResponse>('/auth/login', { username, password }).then((r) => r.data),
  me: () => api.get<UserProfile>('/auth/me').then((r) => r.data),
};

export const vendorApi = {
  searchActive: (search?: string) =>
    api.get<Vendor[]>('/vendors', { params: search ? { search } : undefined })
      .then((r) => r.data),
  currentBankAccount: (vendorId: number) =>
    api.get<VendorBankAccount | null>(`/vendors/${vendorId}/current-bank-account`)
      .then((r) => r.data),
  bankAccountHistory: (vendorId: number) =>
    api.get<VendorBankAccount[]>(`/vendors/${vendorId}/bank-account-history`)
      .then((r) => r.data),
};

export interface CreateBankChangePayload {
  vendorId: number;
  proposedAccountHolderName: string;
  proposedBankName: string;
  proposedAccountReference: string;
  proposedRoutingCode: string;
  reason: string;
  evidenceReference: string;
}

export const bankChangeApi = {
  create: (payload: CreateBankChangePayload) =>
    api.post<BankChangeRequest>('/bank-change-requests', payload).then((r) => r.data),
  mine: (page = 0, size = 20) =>
    api.get<Page<BankChangeRequest>>('/bank-change-requests/mine', { params: { page, size } })
      .then((r) => r.data),
  list: (status?: BankChangeStatus, page = 0, size = 20) =>
    api.get<Page<BankChangeRequest>>('/bank-change-requests',
      { params: { status, page, size } }).then((r) => r.data),
  get: (requestId: number) =>
    api.get<BankChangeRequest>(`/bank-change-requests/${requestId}`).then((r) => r.data),
  blocked: () =>
    api.get<BankChangeRequest[]>('/bank-change-requests/blocked').then((r) => r.data),
};

export const approvalApi = {
  myTasks: (page = 0, size = 20) =>
    api.get<Page<ApprovalTask>>('/approvals/my-tasks', { params: { page, size } })
      .then((r) => r.data),
  myHistory: (page = 0, size = 20) =>
    api.get<Page<ApprovalTask>>('/approvals/my-history', { params: { page, size } })
      .then((r) => r.data),
  allPending: (page = 0, size = 20) =>
    api.get<Page<ApprovalTask>>('/approvals/pending', { params: { page, size } })
      .then((r) => r.data),
  decide: (taskId: number, approved: boolean, comment: string) =>
    api.post<BankChangeRequest>(`/approvals/tasks/${taskId}/decision`, { approved, comment })
      .then((r) => r.data),
  eligibleReplacements: (taskId: number) =>
    api.get<ApproverOption[]>(`/approvals/tasks/${taskId}/eligible-replacements`)
      .then((r) => r.data),
  reassign: (taskId: number, replacementUserId: number, reason: string) =>
    api.post<ApprovalTask>(`/approvals/tasks/${taskId}/reassign`,
      { replacementUserId, reason }).then((r) => r.data),
};

export interface CreatePaymentPayload {
  vendorId: number;
  amount: number;
  currency: string;
  dueDate: string;
  description: string;
}

export const paymentApi = {
  create: (payload: CreatePaymentPayload) =>
    api.post<Payment>('/payments', payload).then((r) => r.data),
  list: (status?: PaymentStatus, page = 0, size = 20) =>
    api.get<Page<Payment>>('/payments', { params: { status, page, size } }).then((r) => r.data),
  get: (paymentId: number) =>
    api.get<Payment>(`/payments/${paymentId}`).then((r) => r.data),
  eligibility: (paymentId: number) =>
    api.get<PaymentEligibility>(`/payments/${paymentId}/eligibility`).then((r) => r.data),
  process: (paymentId: number) =>
    api.post<Payment>(`/payments/${paymentId}/process`).then((r) => r.data),
};

export const holdApi = {
  listActive: (page = 0, size = 20) =>
    api.get<Page<PaymentHold>>('/holds', { params: { page, size } }).then((r) => r.data),
  release: (holdId: number, justification: string) =>
    api.post<PaymentHold>(`/holds/${holdId}/release`, { justification }).then((r) => r.data),
};

export const overrideApi = {
  request: (bankChangeRequestId: number, justification: string, evidenceReference: string) =>
    api.post<EmergencyOverride>('/overrides',
      { bankChangeRequestId, justification, evidenceReference }).then((r) => r.data),
  decide: (overrideId: number, approved: boolean, comment: string) =>
    api.post<EmergencyOverride>(`/overrides/decision/${overrideId}`, { approved, comment })
      .then((r) => r.data),
  list: (status?: string, page = 0, size = 20) =>
    api.get<Page<EmergencyOverride>>('/overrides', { params: { status, page, size } })
      .then((r) => r.data),
};

export const notificationApi = {
  list: (page = 0, size = 20) =>
    api.get<Page<Notification>>('/notifications', { params: { page, size } })
      .then((r) => r.data),
  summary: () =>
    api.get<{ unreadCount: number }>('/notifications/summary').then((r) => r.data),
  markRead: (notificationId: number) =>
    api.post<Notification>(`/notifications/${notificationId}/read`).then((r) => r.data),
};

export const auditApi = {
  list: (params: { action?: string; entityType?: string; entityId?: number;
                   page?: number; size?: number }) =>
    api.get<Page<AuditLog>>('/audit', { params }).then((r) => r.data),
};
