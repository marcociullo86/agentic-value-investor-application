import { apiPost } from '@/lib/api/client';

/**
 * Auth API wrapper (TSK-034). Owned by Track B; thin typed surface over
 * `apiPost` so components and the store don't hardcode endpoint paths.
 *
 * Schema reference: design_&_architecture/api/openapi.yaml §components.schemas
 * (RegisterRequest, LoginRequest, RefreshRequest, TokenPair, UserProfile).
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

export interface RefreshRequest {
  readonly refreshToken: string;
}

export interface TokenPair {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly expiresInSeconds: number;
}

export interface UserProfile {
  readonly id: string;
  readonly email: string;
  readonly displayName: string | null;
  readonly createdAt: string;
}

export async function register(body: RegisterRequest): Promise<UserProfile> {
  const result = await apiPost<UserProfile, RegisterRequest>(
    '/api/auth/register',
    body,
  );
  return result.data;
}

export async function login(body: LoginRequest): Promise<TokenPair> {
  const result = await apiPost<TokenPair, LoginRequest>('/api/auth/login', body);
  return result.data;
}

export async function refreshTokens(refreshToken: string): Promise<TokenPair> {
  const result = await apiPost<TokenPair, RefreshRequest>(
    '/api/auth/refresh',
    { refreshToken },
  );
  return result.data;
}

export async function logout(refreshToken: string | null): Promise<void> {
  await apiPost<void, RefreshRequest | undefined>(
    '/api/auth/logout',
    refreshToken ? { refreshToken } : undefined,
  );
}
