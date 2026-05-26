'use client';

import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { useLogout } from '@/hooks/use-logout';
import {
  Modal,
  ModalContent,
  ModalDescription,
  ModalTitle,
} from '@/components/ui/Modal';
import { Button } from '@/components/ui/Button';

const IDLE_TIMEOUT_MS =
  (Number(process.env.NEXT_PUBLIC_IDLE_TIMEOUT_MINUTES) || 15) * 60 * 1000;

const ABSOLUTE_TIMEOUT_MS =
  (Number(process.env.NEXT_PUBLIC_ABSOLUTE_TIMEOUT_HOURS) || 8) *
  60 *
  60 *
  1000;

const PROMPT_COUNTDOWN_S = 60;
const THROTTLE_MS = 1_000;

const ACTIVITY_EVENTS: ReadonlyArray<keyof DocumentEventMap> = [
  'mousemove',
  'keydown',
  'click',
  'scroll',
  'touchstart',
];

const SESSION_START_KEY = '__idle_session_start';

function readSessionStart(): number {
  if (typeof window === 'undefined') return Date.now();
  const stored = sessionStorage.getItem(SESSION_START_KEY);
  if (stored) return Number(stored);
  const now = Date.now();
  sessionStorage.setItem(SESSION_START_KEY, String(now));
  return now;
}

/**
 * IdleTimeoutProvider (TSK-215).
 *
 * Wraps children and enforces two independent session timeouts
 * for authenticated users:
 *
 * 1. **Idle timeout** — after IDLE_TIMEOUT_MS of inactivity an
 *    accessible prompt is shown. If the user does not click
 *    "Estendi sessione" within PROMPT_COUNTDOWN_S seconds the
 *    session is cleared and the user is redirected to /login.
 *
 * 2. **Absolute timeout** — after ABSOLUTE_TIMEOUT_MS from
 *    session start the session is unconditionally cleared,
 *    regardless of activity.
 *
 * Activity events (mousemove, keydown, click, scroll, touchstart)
 * are throttled to ~1 event/s for performance.
 */
export function IdleTimeoutProvider({
  children,
}: {
  readonly children: ReactNode;
}): ReactNode {
  const accessToken = useAuthStore((s) => s.accessToken);
  const { logout } = useLogout();

  const [promptOpen, setPromptOpen] = useState(false);
  const [countdown, setCountdown] = useState(PROMPT_COUNTDOWN_S);

  const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const countdownRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const absoluteTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastFiredRef = useRef(0);
  const promptOpenRef = useRef(false);
  const extendBtnRef = useRef<HTMLButtonElement | null>(null);

  const isAuthenticated = Boolean(accessToken);

  useEffect(() => {
    promptOpenRef.current = promptOpen;
  }, [promptOpen]);

  const performLogout = useCallback((): void => {
    setPromptOpen(false);
    void logout();
  }, [logout]);

  const clearIdleTimer = useCallback((): void => {
    if (idleTimerRef.current) {
      clearTimeout(idleTimerRef.current);
      idleTimerRef.current = null;
    }
  }, []);

  const clearCountdownTimer = useCallback((): void => {
    if (countdownRef.current) {
      clearInterval(countdownRef.current);
      countdownRef.current = null;
    }
  }, []);

  const startIdleTimer = useCallback((): void => {
    clearIdleTimer();
    idleTimerRef.current = setTimeout(() => {
      setPromptOpen(true);
      setCountdown(PROMPT_COUNTDOWN_S);
    }, IDLE_TIMEOUT_MS);
  }, [clearIdleTimer]);

  const handleExtend = useCallback((): void => {
    setPromptOpen(false);
    clearCountdownTimer();
    startIdleTimer();
  }, [clearCountdownTimer, startIdleTimer]);

  // --- Prompt countdown ---
  useEffect(() => {
    if (!promptOpen) return;

    requestAnimationFrame(() => {
      extendBtnRef.current?.focus();
    });

    setCountdown(PROMPT_COUNTDOWN_S);
    countdownRef.current = setInterval(() => {
      setCountdown((prev) => Math.max(prev - 1, 0));
    }, 1_000);

    return clearCountdownTimer;
  }, [promptOpen, clearCountdownTimer]);

  useEffect(() => {
    if (promptOpen && countdown === 0) {
      performLogout();
    }
  }, [promptOpen, countdown, performLogout]);

  // --- Activity listeners (idle reset, throttled) ---
  useEffect(() => {
    if (!isAuthenticated) return;

    const onActivity = (): void => {
      const now = Date.now();
      if (now - lastFiredRef.current < THROTTLE_MS) return;
      lastFiredRef.current = now;
      if (!promptOpenRef.current) startIdleTimer();
    };

    for (const event of ACTIVITY_EVENTS) {
      document.addEventListener(event, onActivity, { passive: true });
    }

    startIdleTimer();

    return () => {
      for (const event of ACTIVITY_EVENTS) {
        document.removeEventListener(event, onActivity);
      }
      clearIdleTimer();
    };
  }, [isAuthenticated, startIdleTimer, clearIdleTimer]);

  // --- Absolute timeout (never reset) ---
  useEffect(() => {
    if (!isAuthenticated) return;

    const start = readSessionStart();
    const remaining = ABSOLUTE_TIMEOUT_MS - (Date.now() - start);

    if (remaining <= 0) {
      performLogout();
      return;
    }

    absoluteTimerRef.current = setTimeout(performLogout, remaining);

    return () => {
      if (absoluteTimerRef.current) {
        clearTimeout(absoluteTimerRef.current);
        absoluteTimerRef.current = null;
      }
    };
  }, [isAuthenticated, performLogout]);

  if (!isAuthenticated) return <>{children}</>;

  return (
    <>
      {children}
      <Modal
        open={promptOpen}
        onOpenChange={(open) => {
          if (!open) handleExtend();
        }}
      >
        <ModalContent
          role="alertdialog"
          aria-labelledby="idle-timeout-title"
          aria-describedby="idle-timeout-desc"
          onInteractOutside={(e) => e.preventDefault()}
        >
          <ModalTitle
            id="idle-timeout-title"
            className="text-lg font-semibold text-on-surface"
          >
            Sessione in scadenza
          </ModalTitle>
          <ModalDescription
            id="idle-timeout-desc"
            className="text-sm text-on-surface-variant"
          >
            La tua sessione sta per scadere per inattività.
          </ModalDescription>
          <p
            aria-live="assertive"
            className="text-center text-2xl font-semibold tabular-nums text-on-surface"
          >
            {countdown}s
          </p>
          <div className="flex justify-end">
            <Button
              ref={extendBtnRef}
              variant="primary"
              onClick={handleExtend}
            >
              Estendi sessione
            </Button>
          </div>
        </ModalContent>
      </Modal>
    </>
  );
}
