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
 * A non-httpOnly `sessionExpired` cookie is written/cleared in sync with
 * the `sessionExpired` flag (TSK-043 / wave A6 fix): the middleware
 * (`middleware.ts §1`) reads it to redirect to `/login?expired=true` and
 * deletes it in the same response. Without this cookie hint the middleware
 * branch is unreachable from a client-side 401 interceptor.
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

/**
 * Hint cookie consumato dal middleware Edge (`middleware.ts §1`): se presente
 * il middleware redirige a `/login?expired=true` ed elimina il cookie nella
 * stessa response (TSK-043).
 */
function setSessionExpiredCookie(): void {
  if (typeof document !== 'undefined') {
    document.cookie = 'sessionExpired=true; path=/; SameSite=Strict';
  }
}

function clearSessionExpiredCookie(): void {
  if (typeof document !== 'undefined') {
    document.cookie = 'sessionExpired=; path=/; max-age=0';
  }
}

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  expiresAt: null,
  user: null,
  rehydrationStatus: 'pending',
  sessionExpired: false,

  setUser: (user: UserProfile | null): void => set({ user }),

  setSessionExpired: (value: boolean): void => {
    if (value) {
      setSessionExpiredCookie();
    } else {
      clearSessionExpiredCookie();
    }
    set({ sessionExpired: value });
  },

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
    // Pulisce prima il marker di sessione scaduta (TSK-043 fix), poi imposta
    // il hint di sessione attiva. L'ordine è rilevante solo per ambienti
    // di test con shim string del document.cookie; in browser reali i due
    // cookie coesistono indipendentemente.
    clearSessionExpiredCookie();
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
