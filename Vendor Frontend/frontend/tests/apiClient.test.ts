import { describe, expect, it } from 'vitest';
import { ApiError, toApiError } from '../src/api/client';

describe('API error translation', () => {
  it('keeps the backend business code and message', () => {
    const error = toApiError({
      response: {
        data: {
          timestamp: '2026-11-11T10:00:00Z',
          status: 403,
          error: 'Forbidden',
          code: 'MAKER_CANNOT_APPROVE',
          message: 'The Maker of a request may never approve it',
          path: '/api/approvals/tasks/1/decision',
        },
      },
    });

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(403);
    expect(error.code).toBe('MAKER_CANNOT_APPROVE');
    expect(error.message).toBe('The Maker of a request may never approve it');
  });

  it('surfaces field errors so a form can attach them', () => {
    const error = toApiError({
      response: {
        data: {
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'One or more fields are invalid',
          fieldErrors: [{ field: 'reason', message: 'Please give a meaningful reason' }],
        },
      },
    });

    expect(error.fieldErrors).toHaveLength(1);
    expect(error.fieldErrors[0].field).toBe('reason');
  });

  it('explains an unreachable backend rather than failing silently', () => {
    const error = toApiError({ request: {} });
    expect(error.code).toBe('NETWORK_ERROR');
    expect(error.message).toContain('9090');
  });
});
