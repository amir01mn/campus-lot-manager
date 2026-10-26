import type { FieldViolation, ProblemDetail } from './types';

/** Base path of the REST API. Relative, so the Vite dev server (and nginx in production) proxy it. */
export const API_BASE = '/api';

/** An HTTP error from the API, carrying the RFC 7807 body when there is one. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get code(): string | undefined {
    return this.problem.code;
  }

  get fieldErrors(): FieldViolation[] {
    return this.problem.errors ?? [];
  }
}

/** Thrown when the server can't be reached at all. */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('Could not reach the server. Check your connection and try again.', { cause });
    this.name = 'NetworkError';
  }
}

let authToken: string | null = null;
let onUnauthorized: (() => void) | null = null;

/** Sets the bearer token sent with every request (kept in memory; the auth feature persists it). */
export function setAuthToken(token: string | null): void {
  authToken = token;
}

/** Registers the callback run when an authenticated request answers 401 (expired or revoked token). */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler;
}

type Query = Record<string, string | number | boolean | null | undefined>;

export type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  query?: Query;
  body?: unknown;
  signal?: AbortSignal;
};

export function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
    }
  }
  const qs = params.toString();
  return `${API_BASE}${path}${qs ? `?${qs}` : ''}`;
}

function isProblem(value: unknown): value is ProblemDetail {
  return typeof value === 'object' && value !== null;
}

async function readProblem(response: Response): Promise<ProblemDetail> {
  try {
    const body: unknown = await response.json();
    if (isProblem(body)) return body;
  } catch {
    // not JSON (e.g. a proxy error page); fall through to a generic problem
  }
  return { status: response.status, title: response.statusText || 'Request failed' };
}

/** Performs a JSON request against the API. Resolves with the parsed body (or `undefined` for 204). */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', query, body, signal } = options;
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const tokenAtRequest = authToken;
  if (tokenAtRequest) headers.Authorization = `Bearer ${tokenAtRequest}`;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    throw new NetworkError(error);
  }

  if (!response.ok) {
    const problem = await readProblem(response);
    // Only a token that was actually sent can have expired; a failed login is also a 401.
    if (response.status === 401 && tokenAtRequest && tokenAtRequest === authToken) onUnauthorized?.();
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}
