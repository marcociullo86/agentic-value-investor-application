import { render, screen, act, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { axe } from 'vitest-axe';

import { IdleTimeoutProvider } from '../idle-timeout-provider';

/* ------------------------------------------------------------------ */
/*  Constants (matching implementation defaults)                       */
/* ------------------------------------------------------------------ */

const FIFTEEN_MINUTES = 15 * 60 * 1_000;
const EIGHT_HOURS = 8 * 60 * 60 * 1_000;
const SESSION_START_KEY = '__idle_session_start';

/* ------------------------------------------------------------------ */
/*  Mocks                                                              */
/* ------------------------------------------------------------------ */

const mockLogout = vi.fn().mockResolvedValue(undefined);
vi.mock('@/hooks/use-logout', () => ({
  useLogout: () => ({ logout: mockLogout }),
}));

let mockAccessToken: string | null = 'fake-token';

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: Object.assign(
    (selector: (s: Record<string, unknown>) => unknown) =>
      selector({ accessToken: mockAccessToken }),
    { getState: () => ({ accessToken: mockAccessToken }) },
  ),
}));

/* ------------------------------------------------------------------ */
/*  jsdom polyfills for Radix pointer-capture                          */
/* ------------------------------------------------------------------ */

beforeEach(() => {
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.setPointerCapture ??= () => {};
  Element.prototype.releasePointerCapture ??= () => {};
});

/* ------------------------------------------------------------------ */
/*  Setup / teardown                                                   */
/* ------------------------------------------------------------------ */

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: false });
  mockAccessToken = 'fake-token';
  mockLogout.mockClear();
  sessionStorage.clear();
});

afterEach(() => {
  vi.useRealTimers();
});

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

function renderProvider(authenticated = true) {
  mockAccessToken = authenticated ? 'fake-token' : null;
  return render(
    <IdleTimeoutProvider>
      <div>App content</div>
    </IdleTimeoutProvider>,
  );
}

/**
 * Advances fake clock 1 s at a time, each tick wrapped in its own
 * act() so React flushes state + effects between interval callbacks.
 */
function tickSeconds(seconds: number): void {
  for (let i = 0; i < seconds; i++) {
    act(() => {
      vi.advanceTimersByTime(1_000);
    });
  }
}

/* ================================================================== */
/*  1. Timer behavior (US-077 AC)                                      */
/* ================================================================== */

describe('IdleTimeoutProvider — timer behavior', () => {
  it('shows prompt after 15 min of inactivity', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES);
    });

    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toBeInTheDocument();
    expect(screen.getByText('Sessione in scadenza')).toBeInTheDocument();
    expect(
      screen.getByText(/sessione sta per scadere per inattività/i),
    ).toBeInTheDocument();
  });

  it('does not show prompt before 15 min', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES - 1_000);
    });

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
  });

  it('extends session on "Estendi sessione" click and resets timer', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES);
    });
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    fireEvent.click(
      screen.getByRole('button', { name: /estendi sessione/i }),
    );

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES - 1_000);
    });
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(1_000);
    });
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('logs out when prompt countdown reaches zero (no interaction)', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES);
    });
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    tickSeconds(61);

    expect(mockLogout).toHaveBeenCalledTimes(1);
  });

  it('terminates session when absolute timeout exceeded', () => {
    const expired = Date.now() - EIGHT_HOURS - 1_000;
    sessionStorage.setItem(SESSION_START_KEY, String(expired));

    act(() => {
      renderProvider();
    });

    act(() => {
      vi.advanceTimersByTime(0);
    });

    expect(mockLogout).toHaveBeenCalledTimes(1);
  });

  it('resets idle timer on user activity (mousemove/keydown)', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(10 * 60 * 1_000);
    });

    act(() => {
      vi.advanceTimersByTime(1_100);
      fireEvent.mouseMove(document);
    });

    act(() => {
      vi.advanceTimersByTime(14 * 60 * 1_000);
    });
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(1 * 60 * 1_000);
    });
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
  });

  it('does not register listeners or show prompt when not authenticated', () => {
    renderProvider(false);

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES + 61_000);
    });

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(mockLogout).not.toHaveBeenCalled();
  });
});

/* ================================================================== */
/*  2. Accessibility (US-077 AC: prompt accessibile)                   */
/* ================================================================== */

describe('IdleTimeoutProvider — accessibility', () => {
  it('prompt has role="alertdialog" with aria-labelledby and aria-describedby', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES);
    });

    const dialog = screen.getByRole('alertdialog');
    expect(dialog).toHaveAttribute('aria-labelledby', 'idle-timeout-title');
    expect(dialog).toHaveAttribute('aria-describedby', 'idle-timeout-desc');
  });

  it('focuses "Estendi sessione" button when prompt appears', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES);
      vi.advanceTimersByTime(16);
    });

    const extendBtn = screen.getByRole('button', { name: /estendi sessione/i });
    expect(extendBtn).toHaveFocus();
  });

  it('Escape key dismisses prompt and extends session (no logout)', () => {
    renderProvider();

    act(() => {
      vi.advanceTimersByTime(FIFTEEN_MINUTES);
    });
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    fireEvent.keyDown(screen.getByRole('alertdialog'), { key: 'Escape' });

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(mockLogout).not.toHaveBeenCalled();
  });

  it(
    'passes axe-core audit (zero serious/critical violations)',
    async () => {
      renderProvider();

      act(() => {
        vi.advanceTimersByTime(FIFTEEN_MINUTES);
      });

      vi.useRealTimers();

      const results = await axe(document.body, {
        rules: {
          'color-contrast': { enabled: false },
          region: { enabled: false },
        },
      });

      const seriousOrCritical = results.violations.filter(
        (v) => v.impact === 'serious' || v.impact === 'critical',
      );
      expect(seriousOrCritical).toHaveLength(0);
    },
    15_000,
  );
});
