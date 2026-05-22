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
  readonly login: (email: string, password: string) => Promise<void>;
  readonly logout: () => Promise<void>;
  readonly refresh: () => Promise<void>;
  readonly setSession: (
    tokens: { accessToken: string; refreshToken: string },
    user?: UserProfile | null,
  ) => void;
  readonly setUser: (user: UserProfile | null) => void;
}

export type { UserProfile };

export const useAuthStore = create<AuthState>((set, get) => ({
  accessToken: null,
  refreshToken: null,
  user: null,

  setSession: ({ accessToken, refreshToken }, user = undefined): void => {
    set((prev) => ({
      accessToken,
      refreshToken,
      user: user !== undefined ? user : prev.user,
    }));
  },

  setUser: (user: UserProfile | null): void => set({ user }),

  login: async (email: string, password: string): Promise<void> => {
    const tokens = await apiLogin({ email, password });
    set({
      accessToken: tokens.accessToken,
      refreshToken: tokens.refreshToken,
      user: { id: '', email, displayName: null, createdAt: '' },
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
