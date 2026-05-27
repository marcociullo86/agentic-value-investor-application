import { apiPost, apiDelete } from '@/lib/api/client';

/**
 * Auth API wrapper (TSK-034, TSK-211, TSK-232/233).
 *
 * TSK-211: refresh token migrated to httpOnly cookie (TSK-209 BE).
 * The FE no longer sends or receives refreshToken in JSON bodies.
 * The browser attaches the httpOnly cookie automatically on
 * requests to /api/auth/* (credentials: 'include' via withCredentials).
 *
 * TSK-232/233 (US-081): MFA TOTP endpoints (enroll / verify / challenge /
 * recovery / disable). Login response is now `LoginResponse` carrying either
 * the regular access token *or* an `mfaRequired` flag with a short-lived
 * `mfaToken` to be replayed at /api/auth/mfa/challenge or /recovery.
 *
 * Schema reference: design_&_architecture/api/openapi.yaml §components.schemas
 * Backend contracts: src/backend/.../api/MfaController.kt, model/MfaDtos.kt
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
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

/**
 * Login outcome shape (TSK-232/233 — backend `LoginResponse`).
 *
 * - No-MFA path: `{ accessToken, expiresInSeconds, mfaRequired: false }`.
 * - MFA path:    `{ mfaRequired: true, mfaToken }` (no access token,
 *   no refresh cookie set; the FE must complete the challenge).
 */
export interface LoginResponse {
  readonly accessToken?: string;
  readonly expiresInSeconds?: number;
  readonly mfaRequired: boolean;
  readonly mfaToken?: string;
}

export type UserRole = 'ADMIN' | 'USER';

export interface UserProfile {
  readonly id: string;
  readonly email: string;
  readonly displayName: string | null;
  readonly createdAt: string;
  readonly role?: UserRole;
}

/**
 * Initial enrollment material returned by `POST /api/auth/mfa/enroll`.
 * `recoveryCodes` are returned ONCE in plain text — the server stores BCrypt
 * hashes only. The FE must surface them and require the user to confirm
 * they have saved them before navigating away.
 */
export interface MfaEnrollmentResponse {
  readonly secret: string;
  readonly qrCodeUri: string;
  readonly recoveryCodes: readonly string[];
}

export interface MfaVerifyRequest {
  readonly totpCode: string;
}

export interface MfaChallengeRequest {
  readonly mfaToken: string;
  readonly totpCode: string;
}

export interface MfaRecoveryRequest {
  readonly mfaToken: string;
  readonly recoveryCode: string;
}

export interface MfaDisableRequest {
  readonly password: string;
}

export async function register(body: RegisterRequest): Promise<UserProfile> {
  const result = await apiPost<UserProfile, RegisterRequest>(
    '/api/auth/register',
    body,
  );
  return result.data;
}

export async function login(body: LoginRequest): Promise<LoginResponse> {
  const result = await apiPost<LoginResponse, LoginRequest>(
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

/**
 * Start MFA enrollment for the currently authenticated user. Returns
 * the TOTP secret, the otpauth:// provisioning URI to render as QR, and
 * 8 plain-text recovery codes (shown ONCE).
 */
export async function enrollMfa(): Promise<MfaEnrollmentResponse> {
  const result = await apiPost<MfaEnrollmentResponse>('/api/auth/mfa/enroll');
  return result.data;
}

/**
 * Activate MFA after enrollment by proving the user can compute a TOTP code.
 * Returns 204 No Content on success.
 */
export async function verifyMfa(body: MfaVerifyRequest): Promise<void> {
  await apiPost<void, MfaVerifyRequest>('/api/auth/mfa/verify', body);
}

/**
 * Complete an MFA login challenge with a TOTP code. Trades the short-lived
 * `mfaToken` from `POST /api/auth/login` for a regular access token + a
 * refresh-token cookie (Set-Cookie HttpOnly).
 */
export async function challengeMfa(
  body: MfaChallengeRequest,
): Promise<TokenResponse> {
  const result = await apiPost<TokenResponse, MfaChallengeRequest>(
    '/api/auth/mfa/challenge',
    body,
  );
  return result.data;
}

/**
 * Complete an MFA login challenge with a recovery code (one-time use).
 * Same response shape as `challengeMfa`.
 */
export async function recoveryMfa(
  body: MfaRecoveryRequest,
): Promise<TokenResponse> {
  const result = await apiPost<TokenResponse, MfaRecoveryRequest>(
    '/api/auth/mfa/recovery',
    body,
  );
  return result.data;
}

/**
 * Disable MFA after the user re-asserts identity with the account password.
 * Returns 204 No Content on success.
 */
export async function disableMfa(body: MfaDisableRequest): Promise<void> {
  await apiDelete<void>('/api/auth/mfa', { data: body });
}
