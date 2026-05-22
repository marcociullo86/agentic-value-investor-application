import { create } from 'zustand';
import {
  login as apiLogin,
  logout as apiLogout,
  refreshTokens as apiRefresh,
  type UserProfile,
} from '@/lib/api/auth';

/**
 * Auth store (TSK-034 — full impl on top of the TSK-030 skeleton).
 *
 * ADR-006 prefers `httpOnly` cookie for the refresh token; the current backend
 * however returns it inside the `TokenPair` JSON body (OpenAPI contract). We
 * therefore keep both `accessToken` and `refreshToken` in Zustand memory only.
 * Reloading the tab drops both — the user must re-login. The DoD explicitly
 * accepts "reload = re-login or silent refresh" (TSK-034).
 *
 * Reference: design_&_architecture/components/frontend-components.md §useAuthStore.
 */

export interface AuthState {
  readonly accessToken: string | null;
  readonly refreshToken: string | null;
  readonly user: UserProfile | null;
  /**
   * Set to true when the silent refresh fails with 401 (cap assoluto raggiunto
   * o refresh token revocato/scaduto, ADR-010 §3). Toggled back to false on
   * a successful login or when the user dismisses the banner via "Accedi".
   * Drives the `SessionExpiredBanner` mounted in the root layout (TSK-043).
   */
  readonly sessionExpired: boolean;
  readonly login: (email: string, password: string) => Promise<void>;
  readonly logout: () => Promise<void>;
  readonly refresh: () => Promise<void>;
  readonly setSession: (
    tokens: { accessToken: string; refreshToken: string },
    user?: UserProfile | null,
  ) => void;
  readonly setUser: (user: UserProfile | null) => void;
  readonly setSessionExpired: (value: boolean) => void;
  /**
   * Clears all session state without trying to call the backend `/logout` —
   * used by the 401 interceptor when the refresh chain is no longer
   * recoverable. Kept distinct from `logout()` (best-effort backend call) to
   * avoid blocking the UX behind an HTTP round-trip on a server that just
   * returned 401.
   */
  readonly clearSession: () => void;
}

export type { UserProfile };

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  user: null,
  sessionExpired: false,

  setSession: ({ accessToken, refreshToken }, user = undefined): void => {
    set((prev) => ({
      accessToken,
      refreshToken,
      user: user !== undefined ? user : prev.user,
    }));
  },

  setUser: (user: UserProfile | null): void => set({ user }),

  setSessionExpired: (value: boolean): void => set({ sessionExpired: value }),

  clearSession: (): void =>
    set({
      accessToken: null,
      refreshToken: null,
      user: null,
    }),

  login: async (email: string, password: string): Promise<void> => {
    const tokens = await apiLogin({ email, password });
    set({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      user: { id: '', email, displayName: null, createdAt: '' },
      // New login always clears the "session expired" banner.
      sessionExpired: false,
    });
  },

  logout: async (): Promise<void> => {
    const refreshToken = get().refreshToken;
    try {
      await apiLogout(refreshToken);
    } catch {
      // Best-effort: clear local state even if the backend rejects the call.
    }
    set({ accessToken: null, refreshToken: null, user: null });
  },

  refresh: async (): Promise<void> => {
    const refreshToken = get().refreshToken;
    if (!refreshToken) {
      throw new Error('No refresh token available');
    }
    const tokens = await apiRefresh(refreshToken);
    set({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
    });
  },
}));
