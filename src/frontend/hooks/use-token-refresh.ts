'use client';

import { useEffect, useRef } from 'react';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { acquireFreshToken } from '@/lib/api/token-refresh-mutex';

const PRE_EXPIRY_BUFFER_MS = 60_000;
const MIN_TIMER_MS = 5_000;

/**
 * useTokenRefresh (TSK-213).
 *
 * Schedules a silent token refresh ~60s before the access token expires.
 * Uses the mutex to ensure a single refresh in flight. On failure the
 * mutex handles logout/session-expired flow.
 *
 * Activate after rehydration is done and the user is authenticated.
 */
export function useTokenRefresh(): void {
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const expiresAt = useAuthStore((s) => s.expiresAt);
  const accessToken = useAuthStore((s) => s.accessToken);

  useEffect(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }

    if (!accessToken || !expiresAt) {
      return;
    }

    const msUntilExpiry = expiresAt - Date.now();
    const delay = Math.max(msUntilExpiry - PRE_EXPIRY_BUFFER_MS, MIN_TIMER_MS);

    timerRef.current = setTimeout(() => {
      void acquireFreshToken().catch(() => {
        // Failure handled inside the mutex (clearSession + sessionExpired).
      });
    }, delay);

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [accessToken, expiresAt]);
}
