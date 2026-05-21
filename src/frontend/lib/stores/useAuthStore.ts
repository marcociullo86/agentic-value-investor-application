import { create } from 'zustand';

/**
 * Auth store (TSK-030 scheleton, completato in TSK-034).
 *
 * Riferimento: design_&_architecture/components/frontend-components.md
 *   §State management → `useAuthStore`.
 *
 * Contratto:
 *  - `accessToken` in memoria, mai persistito (ADR-006).
 *  - `refresh()` consuma cookie httpOnly via endpoint backend.
 *  - `logout()` resetta lo state; UI redirige a /login.
 */

export interface UserProfile {
  readonly email: string;
  readonly displayName: string | null;
}

export interface AuthState {
  readonly accessToken: string | null;
  readonly user: UserProfile | null;
  readonly login: (email: string, password: string) => Promise<void>;
  readonly logout: () => void;
  readonly refresh: () => Promise<void>;
  readonly setAccessToken: (token: string | null) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
  accessToken: null,
  user: null,
  setAccessToken: (token: string | null): void => set({ accessToken: token }),
  // TSK-034 completerà queste implementazioni con chiamate axios reali.
  login: async (_email: string, _password: string): Promise<void> => {
    throw new Error('Not implemented — landing in TSK-034 (auth FE).');
  },
  logout: (): void => set({ accessToken: null, user: null }),
  refresh: async (): Promise<void> => {
    throw new Error('Not implemented — landing in TSK-034 (auth FE).');
  },
}));
