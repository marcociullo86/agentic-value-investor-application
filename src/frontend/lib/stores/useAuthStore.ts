import { create } from 'zustand';
import {
  login as apiLogin,
  logout as apiLogout,
  refreshTokens as apiRefresh,
  type UserProfile,
} from '@/lib/api/auth';

/**
 * Auth store (TSK-034, TSK-211 — cookie-based refresh).
 *
 * TSK-211: refresh token migrated to httpOnly cookie (TSK-209 BE).
 * The FE only holds `accessToken` in Zustand memory (never persisted).
 * On reload (F5) the in-memory token is lost; the AuthProvider bootstrap
 * attempts `POST /api/auth/refresh` (browser sends httpOnly cookie
 * automatically) to silently rehydrate.
 *
 * A non-httpOnly `isAuthenticated` cookie is set on login and cleared
 * on logout — it serves as a hint for the Next.js Edge middleware
 * (TSK-206) to gate protected routes without accessing the httpOnly
 * refresh token.
 *
 * Reference: design_&_architecture/components/frontend-components.md §useAuthStore.
 */

export interface AuthState {
  readonly accessToken: string | null;
  /** Epoch ms when the current access token expires (null if unauthenticated). */
  readonly expiresAt: number | null;
  readonly user: UserProfile | null;
  /**
   * Rehydration state for the bootstrap flow (AuthProvider).
   * - 'pending': initial mount, rehydration not attempted yet
   * - 'rehydrating': refresh call in flight
   * - 'done': rehydration complete (success or failure)
   */
  readonly rehydrationStatus: 'pending' | 'rehydrating' | 'done';
  /**
   * Set to true when the silent refresh fails with 401 (cap assoluto
   * raggiunto o refresh token revocato/scaduto, ADR-010 §3). Toggled
   * back to false on a successful login or when the user dismisses
   * the banner via "Accedi".
   */
  readonly sessionExpired: boolean;
  readonly login: (email: string, password: string) => Promise<void>;
  readonly logout: () => Promise<void>;
  readonly refresh: () => Promise<void>;
  readonly rehydrate: () => Promise<void>;
  readonly setUser: (user: UserProfile | null) => void;
  readonly setSessionExpired: (value: boolean) => void;
  readonly clearSession: () => void;
}

export type { UserProfile };

function setAuthHintCookie(): void {
  if (typeof document !== 'undefined') {
    document.cookie = 'isAuthenticated=true; path=/; SameSite=Strict';
  }
}

function clearAuthHintCookie(): void {
  if (typeof document !== 'undefined') {
    document.cookie = 'isAuthenticated=; path=/; max-age=0';
  }
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  expiresAt: null,
  user: null,
  rehydrationStatus: 'pending',
  sessionExpired: false,

  setUser: (user: UserProfile | null): void => set({ user }),

  setSessionExpired: (value: boolean): void => set({ sessionExpired: value }),

  clearSession: (): void => {
    clearAuthHintCookie();
    set({
      accessToken: null,
      expiresAt: null,
      user: null,
    });
  },

  login: async (email: string, password: string): Promise<void> => {
    const response = await apiLogin({ email, password });
    setAuthHintCookie();
    set({
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
      user: { id: '', email, displayName: null, createdAt: '' },
      sessionExpired: false,
      rehydrationStatus: 'done',
    });
  },

  logout: async (): Promise<void> => {
    try {
      await apiLogout();
    } catch {
      // Best-effort: clear local state even if the backend rejects the call.
    }
    clearAuthHintCookie();
    set({ accessToken: null, user: null });
  },

  refresh: async (): Promise<void> => {
    const response = await apiRefresh();
    set({
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
    });
  },

  rehydrate: async (): Promise<void> => {
    if (get().accessToken) {
      set({ rehydrationStatus: 'done' });
      return;
    }

    set({ rehydrationStatus: 'rehydrating' });
    try {
      const response = await apiRefresh();
      setAuthHintCookie();
      set({
        accessToken: response.accessToken,
        expiresAt: Date.now() + response.expiresInSeconds * 1000,
        rehydrationStatus: 'done',
      });
    } catch {
      clearAuthHintCookie();
      set({
        accessToken: null,
        expiresAt: null,
        user: null,
        rehydrationStatus: 'done',
      });
    }
  },
}));
