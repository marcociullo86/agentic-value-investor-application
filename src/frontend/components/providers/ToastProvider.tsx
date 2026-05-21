'use client';

import type { ReactNode } from 'react';
import {
  ToastProviderPrimitive,
  ToastViewport,
} from '@/components/ui/Toast';

/**
 * Toast root provider: monta Radix Toast.Provider + Viewport.
 * Hook `useToast()` arriva in TSK FE successivo (TSK-021/024 consumeranno
 * notifiche di errore 401/network dalla response interceptor).
 */
export function ToastProvider({
  children,
}: {
  readonly children: ReactNode;
}): ReactNode {
  return (
    <ToastProviderPrimitive swipeDirection="right" duration={5000}>
      {children}
      <ToastViewport />
    </ToastProviderPrimitive>
  );
}
