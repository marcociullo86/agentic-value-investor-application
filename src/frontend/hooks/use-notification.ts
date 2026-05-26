'use client';

import { useContext, useMemo } from 'react';
import {
  NotificationContext,
  type NotificationContextValue,
  type NotificationLevel,
  type NotificationOptions,
} from '@/components/notifications/notification-provider';

type LevelOptions = Omit<NotificationOptions, 'level'>;

export interface UseNotificationReturn {
  notifications: NotificationContextValue['notifications'];
  removeNotification: NotificationContextValue['removeNotification'];
  notify: {
    success: (opts: LevelOptions) => string;
    info: (opts: LevelOptions) => string;
    warning: (opts: LevelOptions) => string;
    error: (opts: LevelOptions) => string;
  };
}

export function useNotification(): UseNotificationReturn {
  const ctx = useContext(NotificationContext);
  if (!ctx) {
    throw new Error(
      'useNotification must be used within a NotificationProvider',
    );
  }

  const notify = useMemo(
    () => ({
      success: (opts: LevelOptions) =>
        ctx.addNotification({ ...opts, level: 'success' as NotificationLevel }),
      info: (opts: LevelOptions) =>
        ctx.addNotification({ ...opts, level: 'info' as NotificationLevel }),
      warning: (opts: LevelOptions) =>
        ctx.addNotification({ ...opts, level: 'warning' as NotificationLevel }),
      error: (opts: LevelOptions) =>
        ctx.addNotification({ ...opts, level: 'error' as NotificationLevel }),
    }),
    [ctx.addNotification],
  );

  return {
    notifications: ctx.notifications,
    removeNotification: ctx.removeNotification,
    notify,
  };
}
