import axios, { AxiosError } from 'axios';
import type { ApiErrorBody } from '../types';

const TOKEN_KEY = 'vfd.accessToken';

export const tokenStore = {
  get: (): string | null => localStorage.getItem(TOKEN_KEY),
  set: (token: string) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
};

/**
 * One axios instance for the whole app. Requests go to /api, which Vite
 * proxies to the backend, so the UI never hardcodes an origin.
 */
export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

api.interceptors.request.use((config) => {
  const token = tokenStore.get();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/** A failed call carries the backend's business code, not just a status. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];

  constructor(status: number, code: string, message: string,
              fieldErrors: { field: string; message: string }[] = []) {
    super(message);
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toApiError(error: unknown): ApiError {
  const axiosError = error as AxiosError<ApiErrorBody>;
  const body = axiosError.response?.data;
  if (body) {
    return new ApiError(body.status, body.code, body.message, body.fieldErrors ?? []);
  }
  if (axiosError.request) {
    return new ApiError(0, 'NETWORK_ERROR',
      'The backend could not be reached. Check that it is running on port 9090.');
  }
  return new ApiError(0, 'UNKNOWN_ERROR', 'Something went wrong');
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // An expired or revoked token means the session is over; clearing it
    // sends the user back to the login screen rather than leaving them
    // clicking into 401s.
    if (error.response?.status === 401 && tokenStore.get()) {
      tokenStore.clear();
      window.location.assign('/login');
    }
    return Promise.reject(toApiError(error));
  },
);
