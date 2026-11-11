import { describe, expect, it } from 'vitest';
import { formatCountdown, formatMoney, humanise } from '../src/utils/format';

describe('formatting helpers', () => {
  it('reads a cooling-off countdown in the largest useful unit', () => {
    expect(formatCountdown(90_000)).toBe('25h 0m remaining');
    expect(formatCountdown(3_700)).toBe('1h 1m remaining');
    expect(formatCountdown(125)).toBe('2m 5s remaining');
    expect(formatCountdown(9)).toBe('9s remaining');
  });

  it('says the window is closed rather than showing a negative count', () => {
    expect(formatCountdown(0)).toBe('Window closed');
    expect(formatCountdown(-60)).toBe('Window closed');
  });

  it('turns backend enum names into readable labels', () => {
    expect(humanise('PENDING_SECOND_APPROVAL')).toBe('Pending Second Approval');
    expect(humanise('ACCOUNTS_PAYABLE')).toBe('Accounts Payable');
  });

  it('falls back to a plain amount for an unknown currency code', () => {
    expect(formatMoney(1234.5, 'USD')).toContain('1,234.50');
    expect(formatMoney(10, 'XYZ123')).toBe('XYZ123 10.00');
  });
});
