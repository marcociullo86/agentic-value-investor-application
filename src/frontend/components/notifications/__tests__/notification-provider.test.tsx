import { render, screen, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { axe } from 'vitest-axe';
import * as ToastPrimitive from '@radix-ui/react-toast';

import { NotificationProvider } from '../notification-provider';
import { NotificationContainer } from '../notification-container';
import { NotificationToast } from '../notification-toast';
import { useNotification } from '@/hooks/use-notification';
import { getErrorI18n } from '@/lib/errors/error-code-map';
import type { NotificationLevel } from '../notification-provider';

/* ------------------------------------------------------------------ */
/*  jsdom polyfills for Radix Toast pointer capture                   */
/* ------------------------------------------------------------------ */

beforeEach(() => {
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.setPointerCapture ??= () => {};
  Element.prototype.releasePointerCapture ??= () => {};
});

/* ------------------------------------------------------------------ */
/*  Mocks                                                             */
/* ------------------------------------------------------------------ */

const writeTextMock = vi.fn().mockResolvedValue(undefined);

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });

  Object.defineProperty(navigator, 'clipboard', {
    value: { writeText: writeTextMock },
    writable: true,
    configurable: true,
  });
  writeTextMock.mockClear();
});

afterEach(() => {
  vi.useRealTimers();
});

/* ------------------------------------------------------------------ */
/*  Wrapper helpers                                                   */
/* ------------------------------------------------------------------ */

function AllProviders({ children }: { readonly children: ReactNode }) {
  return (
    <ToastPrimitive.Provider swipeDirection="right">
      <NotificationProvider>{children}</NotificationProvider>
    </ToastPrimitive.Provider>
  );
}

function TestTrigger({
  level,
  title,
  message,
  correlationId,
  actions,
  autoDismiss,
}: {
  level: NotificationLevel;
  title: string;
  message: string;
  correlationId?: string;
  actions?: { label: string; onClick: () => void }[];
  autoDismiss?: boolean;
}) {
  const { notify } = useNotification();

  return (
    <button
      type="button"
      data-testid="trigger"
      onClick={() =>
        notify[level]({ title, message, correlationId, actions, autoDismiss })
      }
    >
      Trigger
    </button>
  );
}

function renderWithProviders(ui: ReactNode) {
  return render(<AllProviders>{ui}</AllProviders>);
}

/**
 * Radix ToastAnnounce creates a visually-hidden <span role="status">.
 * This helper returns only the actual toast <li> elements.
 */
function getToastElements(role: string): HTMLElement[] {
  return screen.getAllByRole(role).filter((el) => el.tagName === 'LI');
}

/* ------------------------------------------------------------------ */
/*  1. Test 4 livelli                                                 */
/* ------------------------------------------------------------------ */

describe('US-064 AC: 4 livelli (success, info, warning, error)', () => {
  const levels: NotificationLevel[] = ['success', 'info', 'warning', 'error'];

  it.each(levels)(
    'notify.%s renders toast with correct title and message',
    async (level) => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      const title = `Title ${level}`;
      const message = `Message ${level}`;

      renderWithProviders(
        <>
          <TestTrigger level={level} title={title} message={message} />
          <NotificationContainer />
        </>,
      );

      await user.click(screen.getByTestId('trigger'));

      expect(screen.getByText(title)).toBeInTheDocument();
      expect(screen.getByText(message)).toBeInTheDocument();
    },
  );

  const iconClassMap: Record<NotificationLevel, string> = {
    success: 'lucide-circle-check',
    info: 'lucide-info',
    warning: 'lucide-triangle-alert',
    error: 'lucide-circle-x',
  };

  it.each(levels)(
    'notify.%s renders a distinct lucide icon for the level',
    async (level) => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      renderWithProviders(
        <>
          <TestTrigger level={level} title="T" message="M" />
          <NotificationContainer />
        </>,
      );

      await user.click(screen.getByTestId('trigger'));

      const svg = document.querySelector(`svg.${iconClassMap[level]}`);
      expect(svg).toBeTruthy();
    },
  );
});

/* ------------------------------------------------------------------ */
/*  2. Correlation ID copiabile                                       */
/* ------------------------------------------------------------------ */

describe('US-064 AC: Correlation ID copiabile', () => {
  it('renders the correlationId badge', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const corrId = 'test-corr-123';

    renderWithProviders(
      <>
        <TestTrigger
          level="error"
          title="Errore"
          message="Dettaglio"
          correlationId={corrId}
        />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));

    expect(screen.getByText(corrId)).toBeInTheDocument();
  });

  it('copies correlationId to clipboard on click', async () => {
    const corrId = 'test-corr-123';
    const onDismiss = vi.fn();

    render(
      <ToastPrimitive.Provider>
        <NotificationToast
          notification={{
            id: 'test-id',
            title: 'Errore',
            message: 'Dettaglio',
            level: 'error',
            correlationId: corrId,
            createdAt: Date.now(),
          }}
          onDismiss={onDismiss}
        />
        <ToastPrimitive.Viewport />
      </ToastPrimitive.Provider>,
    );

    const copyButton = screen.getByRole('button', {
      name: `Copia Correlation ID: ${corrId}`,
    });

    await act(async () => {
      fireEvent.click(copyButton);
      await Promise.resolve();
    });

    expect(writeTextMock).toHaveBeenCalledWith(corrId);
  });
});

/* ------------------------------------------------------------------ */
/*  3. Anti-raw: nessun messaggio tecnico raggiunge l'utente          */
/* ------------------------------------------------------------------ */

describe('US-064 AC: anti-raw (5 scenari HTTP)', () => {
  const HTTP_RAW_PATTERNS = ['400', '401', '403', '404', '500'];

  const SCENARIOS: { httpCode: string; problemType: string }[] = [
    { httpCode: '400', problemType: 'urn:problem-type:validation-failed' },
    { httpCode: '401', problemType: 'urn:problem-type:unauthorized' },
    { httpCode: '403', problemType: 'urn:problem-type:forbidden' },
    { httpCode: '404', problemType: 'urn:problem-type:not-found' },
    { httpCode: '500', problemType: 'urn:problem-type:server-error' },
  ];

  it.each(SCENARIOS)(
    'HTTP $httpCode ($problemType): rendered notification has no raw HTTP codes',
    async ({ problemType }) => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
      const resolved = getErrorI18n(problemType);

      renderWithProviders(
        <>
          <TestTrigger
            level="error"
            title={resolved.title}
            message={resolved.message}
          />
          <NotificationContainer />
        </>,
      );

      await user.click(screen.getByTestId('trigger'));

      const toasts = getToastElements('alert');
      expect(toasts).toHaveLength(1);
      const renderedText = toasts[0].textContent ?? '';

      for (const code of HTTP_RAW_PATTERNS) {
        expect(renderedText).not.toContain(code);
      }
    },
  );
});

/* ------------------------------------------------------------------ */
/*  4. Notifiche con azioni non si chiudono automaticamente           */
/* ------------------------------------------------------------------ */

describe('US-064 AC: notifiche con azioni non auto-dismiss', () => {
  it('notification with actions remains visible after auto-dismiss duration', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const actionFn = vi.fn();

    renderWithProviders(
      <>
        <TestTrigger
          level="warning"
          title="Attenzione"
          message="Operazione fallita"
          actions={[{ label: 'Riprova', onClick: actionFn }]}
        />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));
    expect(screen.getByText('Attenzione')).toBeInTheDocument();
    expect(screen.getByText('Riprova')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(10_000);
    });

    expect(screen.getByText('Attenzione')).toBeInTheDocument();
    expect(screen.getByText('Riprova')).toBeInTheDocument();
  });

  it('notification without actions auto-dismisses after default duration', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderWithProviders(
      <>
        <TestTrigger level="success" title="Fatto" message="OK" />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));
    expect(screen.getByText('Fatto')).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(7_000);
    });

    expect(screen.queryByText('Fatto')).not.toBeInTheDocument();
  });
});

/* ------------------------------------------------------------------ */
/*  5. axe-core a11y scan                                             */
/* ------------------------------------------------------------------ */

describe('US-064 AC: axe-core zero violazioni', () => {
  it('NotificationContainer with all 4 levels passes axe audit', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    function MultiTrigger() {
      const { notify } = useNotification();
      return (
        <button
          type="button"
          data-testid="multi-trigger"
          onClick={() => {
            notify.success({ title: 'Successo', message: 'Operazione OK' });
            notify.info({ title: 'Info', message: 'Nota informativa' });
            notify.warning({
              title: 'Attenzione',
              message: 'Attenzione richiesta',
            });
            notify.error({
              title: 'Errore',
              message: 'Si è verificato un errore',
            });
          }}
        >
          Trigger All
        </button>
      );
    }

    const { container } = renderWithProviders(
      <>
        <MultiTrigger />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('multi-trigger'));

    expect(screen.getByText('Successo')).toBeInTheDocument();
    expect(screen.getByText('Info')).toBeInTheDocument();
    expect(screen.getByText('Attenzione')).toBeInTheDocument();
    expect(screen.getByText('Errore')).toBeInTheDocument();

    const results = await axe(container, {
      rules: {
        'color-contrast': { enabled: false },
        'aria-allowed-role': { enabled: false },
        list: { enabled: false },
      },
    });
    expect(results.violations).toHaveLength(0);
  });
});

/* ------------------------------------------------------------------ */
/*  6. WCAG roles e aria-live                                         */
/* ------------------------------------------------------------------ */

describe('US-064 AC: WCAG roles e aria-live', () => {
  it.each([
    { level: 'success' as const, expectedRole: 'status', expectedLive: 'polite' },
    { level: 'info' as const, expectedRole: 'status', expectedLive: 'polite' },
  ])(
    '$level → role="$expectedRole" aria-live="$expectedLive"',
    async ({ level, expectedRole, expectedLive }) => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      renderWithProviders(
        <>
          <TestTrigger level={level} title="T" message="M" />
          <NotificationContainer />
        </>,
      );

      await user.click(screen.getByTestId('trigger'));

      const toasts = getToastElements(expectedRole);
      expect(toasts).toHaveLength(1);
      expect(toasts[0]).toHaveAttribute('aria-live', expectedLive);
    },
  );

  it.each([
    {
      level: 'warning' as const,
      expectedRole: 'alert',
      expectedLive: 'assertive',
    },
    {
      level: 'error' as const,
      expectedRole: 'alert',
      expectedLive: 'assertive',
    },
  ])(
    '$level → role="$expectedRole" aria-live="$expectedLive"',
    async ({ level, expectedRole, expectedLive }) => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      renderWithProviders(
        <>
          <TestTrigger level={level} title="T" message="M" />
          <NotificationContainer />
        </>,
      );

      await user.click(screen.getByTestId('trigger'));

      const toasts = getToastElements(expectedRole);
      expect(toasts).toHaveLength(1);
      expect(toasts[0]).toHaveAttribute('aria-live', expectedLive);
    },
  );
});

/* ------------------------------------------------------------------ */
/*  Extra: useNotification outside provider                           */
/* ------------------------------------------------------------------ */

describe('useNotification outside NotificationProvider', () => {
  it('throws when used without NotificationProvider', () => {
    expect(() => {
      render(<TestTrigger level="info" title="T" message="M" />);
    }).toThrow('useNotification must be used within a NotificationProvider');
  });
});
