'use client';

import {
  createContext,
  useCallback,
  useMemo,
  useState,
  type ReactNode,
} from 'react';

export type NotificationLevel = 'success' | 'info' | 'warning' | 'error';

export interface NotificationAction {
  label: string;
  onClick: () => void;
}

export interface NotificationOptions {
  title: string;
  message: string;
  level: NotificationLevel;
  correlationId?: string;
  actions?: NotificationAction[];
  autoDismiss?: boolean;
  duration?: number;
}

export interface Notification extends NotificationOptions {
  id: string;
  createdAt: number;
}

export interface NotificationContextValue {
  notifications: Notification[];
  addNotification: (opts: NotificationOptions) => string;
  removeNotification: (id: string) => void;
}

export const NotificationContext =
  createContext<NotificationContextValue | null>(null);

function generateId(): string {
  return crypto.randomUUID();
}

export function NotificationProvider({
  children,
}: {
  readonly children: ReactNode;
}): ReactNode {
  const [notifications, setNotifications] = useState<Notification[]>([]);

  const removeNotification = useCallback((id: string) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id));
  }, []);

  const addNotification = useCallback((opts: NotificationOptions): string => {
    const id = generateId();
    const notification: Notification = { ...opts, id, createdAt: Date.now() };
    setNotifications((prev) => [...prev, notification]);
    return id;
  }, []);

  const value = useMemo<NotificationContextValue>(
    () => ({ notifications, addNotification, removeNotification }),
    [notifications, addNotification, removeNotification],
  );

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}
