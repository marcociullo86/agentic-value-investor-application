'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import * as ToastPrimitive from '@radix-ui/react-toast';
import { cva, type VariantProps } from 'class-variance-authority';
import {
  CheckCircle2,
  Info,
  AlertTriangle,
  XCircle,
  X,
  Copy,
  Check,
} from 'lucide-react';
import { cn } from '@/lib/utils/cn';
import type { Notification, NotificationLevel } from './notification-provider';

const toastVariants = cva(
  'pointer-events-auto relative flex w-full items-start gap-3 rounded-md border p-4 shadow-md transition-all',
  {
    variants: {
      level: {
        success:
          'border-[var(--color-success)] bg-surface-container text-on-surface',
        info: 'border-[var(--color-info)] bg-surface-container text-on-surface',
        warning:
          'border-[var(--color-warning)] bg-surface-container text-on-surface',
        error:
          'border-[var(--color-error)] bg-surface-container text-on-surface',
      },
    },
    defaultVariants: {
      level: 'info',
    },
  },
);

const ICON_MAP: Record<NotificationLevel, typeof CheckCircle2> = {
  success: CheckCircle2,
  info: Info,
  warning: AlertTriangle,
  error: XCircle,
};

const ICON_COLOR_MAP: Record<NotificationLevel, string> = {
  success: 'text-[var(--color-success)]',
  info: 'text-[var(--color-info)]',
  warning: 'text-[var(--color-warning)]',
  error: 'text-[var(--color-error)]',
};

const DEFAULT_DURATION = 6000;
const LONG_TEXT_DURATION = 8000;
const LONG_TEXT_THRESHOLD = 80;

interface NotificationToastProps
  extends VariantProps<typeof toastVariants> {
  notification: Notification;
  onDismiss: (id: string) => void;
}

export function NotificationToast({
  notification,
  onDismiss,
}: NotificationToastProps) {
  const { id, title, message, level, correlationId, actions } = notification;
  const [copied, setCopied] = useState(false);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const remainingRef = useRef<number>(0);
  const startTimeRef = useRef<number>(0);

  const hasActions = actions && actions.length > 0;
  const textLength = (title + message).length;
  const duration =
    notification.duration ??
    (textLength > LONG_TEXT_THRESHOLD ? LONG_TEXT_DURATION : DEFAULT_DURATION);
  const shouldAutoDismiss = notification.autoDismiss !== false && !hasActions;

  const startTimer = useCallback(
    (remaining: number) => {
      if (!shouldAutoDismiss) return;
      startTimeRef.current = Date.now();
      remainingRef.current = remaining;
      timerRef.current = setTimeout(() => {
        onDismiss(id);
      }, remaining);
    },
    [shouldAutoDismiss, onDismiss, id],
  );

  const pauseTimer = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
      const elapsed = Date.now() - startTimeRef.current;
      remainingRef.current = Math.max(0, remainingRef.current - elapsed);
    }
  }, []);

  useEffect(() => {
    startTimer(duration);
    return () => {
      if (timerRef.current) clearTimeout(timerRef.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePointerEnter = useCallback(() => {
    pauseTimer();
  }, [pauseTimer]);

  const handlePointerLeave = useCallback(() => {
    if (shouldAutoDismiss && remainingRef.current > 0) {
      startTimer(remainingRef.current);
    }
  }, [shouldAutoDismiss, startTimer]);

  const handleFocus = useCallback(() => {
    pauseTimer();
  }, [pauseTimer]);

  const handleBlur = useCallback(() => {
    if (shouldAutoDismiss && remainingRef.current > 0) {
      startTimer(remainingRef.current);
    }
  }, [shouldAutoDismiss, startTimer]);

  const handleCopyCorrelationId = useCallback(async () => {
    if (!correlationId) return;
    try {
      await navigator.clipboard.writeText(correlationId);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    } catch {
      // Clipboard API not available
    }
  }, [correlationId]);

  const isAssertive = level === 'warning' || level === 'error';
  const Icon = ICON_MAP[level];

  return (
    <ToastPrimitive.Root
      className={cn(toastVariants({ level }))}
      onPointerEnter={handlePointerEnter}
      onPointerLeave={handlePointerLeave}
      onFocus={handleFocus}
      onBlur={handleBlur}
      role={isAssertive ? 'alert' : 'status'}
      aria-live={isAssertive ? 'assertive' : 'polite'}
      aria-atomic="true"
      data-auto-dismiss-duration={shouldAutoDismiss ? duration : undefined}
      duration={shouldAutoDismiss ? duration : Infinity}
      open
      onOpenChange={(open) => {
        if (!open) onDismiss(id);
      }}
    >
      <Icon
        className={cn('mt-0.5 h-5 w-5 shrink-0', ICON_COLOR_MAP[level])}
        aria-hidden="true"
      />

      <div className="flex min-w-0 flex-1 flex-col gap-1">
        <ToastPrimitive.Title className="text-sm font-semibold text-on-surface">
          {title}
        </ToastPrimitive.Title>

        <ToastPrimitive.Description className="text-sm text-on-surface/80">
          {message}
        </ToastPrimitive.Description>

        {correlationId && (
          <button
            type="button"
            onClick={handleCopyCorrelationId}
            className="mt-1 inline-flex w-fit items-center gap-1 rounded bg-surface-container-high px-2 py-0.5 text-xs font-mono text-on-surface/70 transition-colors hover:text-on-surface"
            aria-label={`Copia Correlation ID: ${correlationId}`}
          >
            {copied ? (
              <Check className="h-3 w-3" aria-hidden="true" />
            ) : (
              <Copy className="h-3 w-3" aria-hidden="true" />
            )}
            <span>{copied ? 'Copiato!' : correlationId}</span>
          </button>
        )}

        {hasActions && (
          <div className="mt-2 flex gap-2">
            {actions.map((action) => (
              <ToastPrimitive.Action
                key={action.label}
                altText={action.label}
                asChild
              >
                <button
                  type="button"
                  onClick={action.onClick}
                  className="rounded-md bg-primary px-3 py-1 text-xs font-medium text-on-primary"
                >
                  {action.label}
                </button>
              </ToastPrimitive.Action>
            ))}
          </div>
        )}
      </div>

      <ToastPrimitive.Close asChild>
        <button
          type="button"
          className="shrink-0 rounded-md p-1 text-on-surface/60 hover:text-on-surface"
          aria-label="Chiudi notifica"
          onClick={() => onDismiss(id)}
        >
          <X className="h-4 w-4" aria-hidden="true" />
        </button>
      </ToastPrimitive.Close>
    </ToastPrimitive.Root>
  );
}
