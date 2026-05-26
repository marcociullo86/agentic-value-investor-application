'use client';

import { useEffect } from 'react';
import { ToastViewport } from '@/components/ui/Toast';
import { useNotification } from '@/hooks/use-notification';
import { NotificationToast } from './notification-toast';

/**
 * Renders all active notifications from the queue as toast components.
 * Handles global Esc keypress to dismiss the most recent notification.
 */
export function NotificationContainer() {
  const { notifications, removeNotification } = useNotification();

  useEffect(() => {
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape' && notifications.length > 0) {
        const mostRecent = notifications[notifications.length - 1]!;
        removeNotification(mostRecent.id);
      }
    }

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [notifications, removeNotification]);

  return (
    <>
      {notifications.map((notification) => (
        <NotificationToast
          key={notification.id}
          notification={notification}
          onDismiss={removeNotification}
        />
      ))}
      <ToastViewport />
    </>
  );
}
