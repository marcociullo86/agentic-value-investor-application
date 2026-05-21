'use client';

import { useEffect, type ReactNode } from 'react';
import { useAuthStore } from '@/lib/stores/useAuthStore';

/**
 * AuthProvider client component — TSK-030 scheleton.
 *
 * Per ora si limita ad inizializzare lo store. TSK-034 vi monterà:
 *  - tentativo silente di `refresh()` al mount (httpOnly cookie),
 *  - listener per scadenza access token,
 *  - redirect su `/login` per route protette.
 */
export function AuthProvider({
  children,
}: {
  readonly children: ReactNode;
}): ReactNode {
  // Subscribe forza l'idratazione dello store sul client.
  const _accessToken = useAuthStore((s) => s.accessToken);
  useEffect(() => {
    // Hook reservato a TSK-034: silent refresh on mount.
  }, []);
  return <>{children}</>;
}
