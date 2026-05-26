import { apiPost } from '@/lib/api/client';

/**
 * Auth API wrapper (TSK-034, TSK-211).
 *
 * TSK-211: refresh token migrated to httpOnly cookie (TSK-209 BE).
 * The FE no longer sends or receives refreshToken in JSON bodies.
 * The browser attaches the httpOnly cookie automatically on
 * requests to /api/auth/* (credentials: 'include' via withCredentials).
 *
 * Schema reference: design_&_architecture/api/openapi.yaml §components.schemas
 */

export interface RegisterRequest {
  readonly email: string;
  readonly password: string;
  readonly displayName?: string | null;
}

export interface LoginRequest {
  readonly email: string;
  readonly password: string;
}

export interface TokenResponse {
  readonly accessToken: string;
  readonly expiresInSeconds: number;
}

export type UserRole = 'ADMIN' | 'USER';

export interface UserProfile {
  readonly id: string;
  readonly email: string;
  readonly displayName: string | null;
  readonly createdAt: string;
  readonly role?: UserRole;
}

export async function register(body: RegisterRequest): Promise<UserProfile> {
  const result = await apiPost<UserProfile, RegisterRequest>(
    '/api/auth/register',
    body,
  );
  return result.data;
}

export async function login(body: LoginRequest): Promise<TokenResponse> {
  const result = await apiPost<TokenResponse, LoginRequest>(
    '/api/auth/login',
    body,
  );
  return result.data;
}

/**
 * Refresh the access token. The httpOnly cookie carries the refresh token
 * automatically — no body needed.
 */
export async function refreshTokens(): Promise<TokenResponse> {
  const result = await apiPost<TokenResponse>('/api/auth/refresh');
  return result.data;
}

/**
 * Logout. The server reads the refresh token from the httpOnly cookie,
 * revokes it, and clears the cookie via Set-Cookie max-age=0.
 * Returns 204 No Content.
 */
export async function logout(): Promise<void> {
  await apiPost<void>('/api/auth/logout');
}
