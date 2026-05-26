import { render, screen, act, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { axe } from 'vitest-axe';
import * as ToastPrimitive from '@radix-ui/react-toast';
import * as fs from 'node:fs';
import * as path from 'node:path';

import { NotificationProvider } from '../notification-provider';
import { NotificationContainer } from '../notification-container';
import { useNotification } from '@/hooks/use-notification';
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
/*  Timer + clipboard mocks                                           */
/* ------------------------------------------------------------------ */

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
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
  actions,
}: {
  level: NotificationLevel;
  title: string;
  message: string;
  actions?: { label: string; onClick: () => void }[];
}) {
  const { notify } = useNotification();

  return (
    <button
      type="button"
      data-testid="trigger"
      onClick={() => notify[level]({ title, message, actions })}
    >
      Trigger
    </button>
  );
}

function renderWithProviders(ui: ReactNode) {
  return render(<AllProviders>{ui}</AllProviders>);
}

function getToastElements(role: string): HTMLElement[] {
  return screen.getAllByRole(role).filter((el) => el.tagName === 'LI');
}

/* ================================================================== */
/*  1. axe-core zero violazioni per ogni livello                      */
/* ================================================================== */

describe('US-068 AC: axe-core zero violazioni serious/critical', () => {
  const levels: NotificationLevel[] = ['success', 'info', 'warning', 'error'];

  it.each(levels)(
    'level=%s passes axe audit (no serious/critical)',
    async (level) => {
      const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

      const { container } = renderWithProviders(
        <>
          <TestTrigger level={level} title={`Title ${level}`} message={`Message for ${level}`} />
          <NotificationContainer />
        </>,
      );

      await user.click(screen.getByTestId('trigger'));

      const results = await axe(container, {
        rules: {
          'color-contrast': { enabled: false },
          // Radix Toast portals <li> into <ol> viewport; jsdom doesn't reflect portal hierarchy correctly
          list: { enabled: false },
        },
      });

      const seriousOrCritical = results.violations.filter(
        (v) => v.impact === 'serious' || v.impact === 'critical',
      );

      expect(seriousOrCritical).toHaveLength(0);
    },
  );
});

/* ================================================================== */
/*  2. Screen reader roles: role + aria-live                          */
/* ================================================================== */

describe('US-068 AC: screen reader roles', () => {
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
    { level: 'warning' as const, expectedRole: 'alert', expectedLive: 'assertive' },
    { level: 'error' as const, expectedRole: 'alert', expectedLive: 'assertive' },
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

/* ================================================================== */
/*  3. Contrasto token verification (OKLCH lightness)                 */
/* ================================================================== */

describe('US-068 AC: contrasto token verification', () => {
  function parseOklchLightness(value: string): number | null {
    const match = value.match(/oklch\(\s*([\d.]+)/);
    return match ? parseFloat(match[1]) : null;
  }

  function parseCssTokens(cssContent: string): Map<string, string> {
    const tokens = new Map<string, string>();
    const regex = /--([\w-]+):\s*([^;]+);/g;
    let m;
    while ((m = regex.exec(cssContent)) !== null) {
      tokens.set(`--${m[1]}`, m[2].trim());
    }
    return tokens;
  }

  const colorsPath = path.resolve(
    __dirname,
    '../../../styles/tokens/colors.css',
  );
  const colorsDarkPath = path.resolve(
    __dirname,
    '../../../styles/tokens/colors-dark.css',
  );

  it('--color-warning in light mode has L <= 0.65 (contrast >= 3:1 on light bg)', () => {
    const css = fs.readFileSync(colorsPath, 'utf-8');
    const tokens = parseCssTokens(css);
    const warningValue = tokens.get('--color-warning');
    expect(warningValue).toBeDefined();

    const lightness = parseOklchLightness(warningValue!);
    expect(lightness).not.toBeNull();
    expect(lightness!).toBeLessThanOrEqual(0.65);
  });

  it('--color-on-surface has sufficient contrast against --color-surface-container (light mode >= 4.5:1 approximation)', () => {
    const css = fs.readFileSync(colorsPath, 'utf-8');
    const tokens = parseCssTokens(css);

    const onSurfaceL = parseOklchLightness(tokens.get('--color-on-surface')!);
    const surfaceContainerL = parseOklchLightness(tokens.get('--color-surface-container')!);

    expect(onSurfaceL).not.toBeNull();
    expect(surfaceContainerL).not.toBeNull();

    // For WCAG 4.5:1, text (dark) on light bg needs large contrast delta.
    // OKLCH lightness: 0.12 text on 0.95 bg gives ~ 13:1 ratio → OK.
    // We verify the delta is large enough (>= 0.5 guarantees well above 4.5:1).
    const delta = Math.abs(surfaceContainerL! - onSurfaceL!);
    expect(delta).toBeGreaterThanOrEqual(0.5);
  });

  it('--color-on-surface has sufficient contrast against --color-surface-container (dark mode >= 4.5:1 approximation)', () => {
    const css = fs.readFileSync(colorsDarkPath, 'utf-8');
    const tokens = parseCssTokens(css);

    const onSurfaceL = parseOklchLightness(tokens.get('--color-on-surface')!);
    const surfaceContainerL = parseOklchLightness(tokens.get('--color-surface-container')!);

    expect(onSurfaceL).not.toBeNull();
    expect(surfaceContainerL).not.toBeNull();

    const delta = Math.abs(surfaceContainerL! - onSurfaceL!);
    expect(delta).toBeGreaterThanOrEqual(0.5);
  });
});

/* ================================================================== */
/*  4. Auto-dismiss timing (data-auto-dismiss-duration)               */
/* ================================================================== */

describe('US-068 AC: auto-dismiss timing', () => {
  it('short text toast has data-auto-dismiss-duration="6000"', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderWithProviders(
      <>
        <TestTrigger level="success" title="OK" message="Done" />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));

    const toasts = getToastElements('status');
    expect(toasts).toHaveLength(1);
    expect(toasts[0]).toHaveAttribute('data-auto-dismiss-duration', '6000');
  });

  it('long text (> 80 chars) toast has data-auto-dismiss-duration="8000"', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const longMessage = 'A'.repeat(81);

    renderWithProviders(
      <>
        <TestTrigger level="info" title="" message={longMessage} />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));

    const toasts = getToastElements('status');
    expect(toasts).toHaveLength(1);
    expect(toasts[0]).toHaveAttribute('data-auto-dismiss-duration', '8000');
  });

  it('toast with actions has no data-auto-dismiss-duration (no auto-dismiss)', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderWithProviders(
      <>
        <TestTrigger
          level="warning"
          title="Azione"
          message="Richiesta"
          actions={[{ label: 'Riprova', onClick: () => {} }]}
        />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));

    const toasts = getToastElements('alert');
    expect(toasts).toHaveLength(1);
    expect(toasts[0]).not.toHaveAttribute('data-auto-dismiss-duration');
  });
});

/* ================================================================== */
/*  5. Esc dismiss                                                    */
/* ================================================================== */

describe('US-068 AC: Esc chiude la notifica più recente', () => {
  it('pressing Escape removes the most recent notification', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });

    renderWithProviders(
      <>
        <TestTrigger level="info" title="Nota" message="Info test" />
        <NotificationContainer />
      </>,
    );

    await user.click(screen.getByTestId('trigger'));
    expect(screen.getByText('Nota')).toBeInTheDocument();

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(screen.queryByText('Nota')).not.toBeInTheDocument();
  });
});

/* ================================================================== */
/*  6. Distinguibilità senza colore (icone distinte + aria-hidden)    */
/* ================================================================== */

describe('US-068 AC: distinguibilità senza colore', () => {
  const iconClassMap: Record<NotificationLevel, string> = {
    success: 'lucide-circle-check',
    info: 'lucide-info',
    warning: 'lucide-triangle-alert',
    error: 'lucide-circle-x',
  };

  const levels: NotificationLevel[] = ['success', 'info', 'warning', 'error'];

  it.each(levels)(
    '%s renders a unique icon (not shared with other levels)',
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

  it('all icons are distinct from each other', () => {
    const classes = Object.values(iconClassMap);
    const uniqueClasses = new Set(classes);
    expect(uniqueClasses.size).toBe(4);
  });

  it.each(levels)(
    '%s icon has aria-hidden="true"',
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
      expect(svg!.getAttribute('aria-hidden')).toBe('true');
    },
  );
});
