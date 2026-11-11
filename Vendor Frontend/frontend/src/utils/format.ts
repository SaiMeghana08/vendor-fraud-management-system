import type { BankChangeStatus, PaymentStatus } from '../types';

export function formatDateTime(value?: string): string {
  if (!value) return '—';
  return new Date(value).toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit',
    hour: '2-digit', minute: '2-digit',
  });
}

export function formatDate(value?: string): string {
  if (!value) return '—';
  return new Date(value).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: '2-digit',
  });
}

export function formatMoney(amount: number, currency: string): string {
  try {
    return new Intl.NumberFormat(undefined, { style: 'currency', currency }).format(amount);
  } catch {
    return `${currency} ${amount.toFixed(2)}`;
  }
}

/** Turns a remaining-seconds count into a readable countdown. */
export function formatCountdown(totalSeconds: number): string {
  if (totalSeconds <= 0) return 'Window closed';
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  if (hours > 0) return `${hours}h ${minutes}m remaining`;
  if (minutes > 0) return `${minutes}m ${seconds}s remaining`;
  return `${seconds}s remaining`;
}

export function humanise(value: string): string {
  return value
    .toLowerCase()
    .split('_')
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join(' ');
}

export const bankChangeStatusTone: Record<BankChangeStatus, string> = {
  PENDING_FIRST_APPROVAL: 'amber',
  PENDING_SECOND_APPROVAL: 'amber',
  COOLING_OFF: 'blue',
  ACTIVATED: 'green',
  REJECTED: 'rose',
  CANCELLED: 'slate',
  ACTIVATION_BLOCKED: 'rose',
};

export const paymentStatusTone: Record<PaymentStatus, string> = {
  CREATED: 'blue',
  ON_HOLD: 'amber',
  PROCESSED: 'green',
  CANCELLED: 'slate',
};
