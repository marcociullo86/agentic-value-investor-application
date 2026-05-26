import { act, renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ThemeProvider } from '../theme-provider';
import { useTheme } from '@/hooks/use-theme';

/* ------------------------------------------------------------------ */
/*  matchMedia mock                                                   */
/* ------------------------------------------------------------------ */

type MediaQueryHandler = (e: { matches: boolean }) => void;

let darkMatches = false;
const mediaListeners: MediaQueryHandler[] = [];

function createMockMediaQueryList(query: string): MediaQueryList {
  return {
    matches: query.includes('dark') ? darkMatches : false,
    media: query,
    onchange: null,
    addEventListener: (_event: string, handler: MediaQueryHandler) => {
      mediaListeners.push(handler);
    },
    removeEventListener: (_event: string, handler: MediaQueryHandler) => {
      const idx = mediaListeners.indexOf(handler);
      if (idx !== -1) mediaListeners.splice(idx, 1);
    },
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  } as unknown as MediaQueryList;
}

function simulateSystemThemeChange(toDark: boolean) {
  darkMatches = toDark;
  mediaListeners.forEach((h) => h({ matches: toDark }));
}

/* ------------------------------------------------------------------ */
/*  localStorage mock                                                 */
/* ------------------------------------------------------------------ */

const localStorageMock = (() => {
  let store: Record<string, string> = {};
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store[key] = value;
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key];
    }),
    clear: vi.fn(() => {
      store = {};
    }),
    get length() {
      return Object.keys(store).length;
    },
    key: vi.fn((_i: number) => null),
  };
})();

/* ------------------------------------------------------------------ */
/*  Setup / teardown                                                  */
/* ------------------------------------------------------------------ */

beforeEach(() => {
  darkMatches = false;
  mediaListeners.length = 0;
  localStorageMock.clear();
  vi.clearAllMocks();

  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn(createMockMediaQueryList),
  });
  Object.defineProperty(window, 'localStorage', {
    writable: true,
    value: localStorageMock,
  });

  document.documentElement.classList.remove('dark');
});

afterEach(() => {
  document.documentElement.classList.remove('dark');
});

/* ------------------------------------------------------------------ */
/*  Wrapper helper                                                    */
/* ------------------------------------------------------------------ */

function wrapper({ children }: { readonly children: ReactNode }) {
  return <ThemeProvider>{children}</ThemeProvider>;
}

/* ------------------------------------------------------------------ */
/*  Tests                                                             */
/* ------------------------------------------------------------------ */

describe('ThemeProvider + useTheme', () => {
  // ----- Default system preference -----------------------------------

  describe('default system preference', () => {
    it('applies dark class when OS prefers dark', () => {
      darkMatches = true;

      renderHook(() => useTheme(), { wrapper });

      expect(document.documentElement.classList.contains('dark')).toBe(true);
    });

    it('does not apply dark class when OS prefers light', () => {
      darkMatches = false;

      renderHook(() => useTheme(), { wrapper });

      expect(document.documentElement.classList.contains('dark')).toBe(false);
    });
  });

  // ----- Toggle theme ------------------------------------------------

  describe('toggleTheme', () => {
    it('toggles from light to dark', () => {
      darkMatches = false;

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.toggleTheme();
      });

      expect(document.documentElement.classList.contains('dark')).toBe(true);
      expect(result.current.theme).toBe('dark');
    });

    it('toggles from dark to light', () => {
      darkMatches = true;

      const { result } = renderHook(() => useTheme(), { wrapper });
      expect(document.documentElement.classList.contains('dark')).toBe(true);

      act(() => {
        result.current.toggleTheme();
      });

      expect(document.documentElement.classList.contains('dark')).toBe(false);
      expect(result.current.theme).toBe('light');
    });

    it('toggles from system-dark to light', () => {
      darkMatches = true;

      const { result } = renderHook(() => useTheme(), { wrapper });
      expect(result.current.theme).toBe('system');

      act(() => {
        result.current.toggleTheme();
      });

      expect(document.documentElement.classList.contains('dark')).toBe(false);
      expect(result.current.theme).toBe('light');
    });
  });

  // ----- Persistence via localStorage --------------------------------

  describe('persistence', () => {
    it('persists dark theme to localStorage', () => {
      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('dark');
      });

      expect(localStorageMock.setItem).toHaveBeenCalledWith('theme', 'dark');
    });

    it('persists light theme to localStorage', () => {
      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('light');
      });

      expect(localStorageMock.setItem).toHaveBeenCalledWith('theme', 'light');
    });

    it('restores dark theme from localStorage on mount', () => {
      localStorageMock.setItem('theme', 'dark');
      vi.mocked(localStorageMock.setItem).mockClear();

      renderHook(() => useTheme(), { wrapper });

      expect(document.documentElement.classList.contains('dark')).toBe(true);
    });

    it('restores light theme from localStorage on mount', () => {
      darkMatches = true;
      localStorageMock.setItem('theme', 'light');
      vi.mocked(localStorageMock.setItem).mockClear();

      renderHook(() => useTheme(), { wrapper });

      expect(document.documentElement.classList.contains('dark')).toBe(false);
    });
  });

  // ----- System mode reset -------------------------------------------

  describe('system mode reset', () => {
    it('removes localStorage entry when set to system', () => {
      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('dark');
      });
      expect(localStorageMock.setItem).toHaveBeenCalledWith('theme', 'dark');

      act(() => {
        result.current.setTheme('system');
      });

      expect(localStorageMock.removeItem).toHaveBeenCalledWith('theme');
      expect(result.current.theme).toBe('system');
    });

    it('follows OS preference after reset to system (dark)', () => {
      darkMatches = true;
      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('light');
      });
      expect(document.documentElement.classList.contains('dark')).toBe(false);

      act(() => {
        result.current.setTheme('system');
      });

      expect(document.documentElement.classList.contains('dark')).toBe(true);
    });

    it('follows OS preference after reset to system (light)', () => {
      darkMatches = false;
      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('dark');
      });
      expect(document.documentElement.classList.contains('dark')).toBe(true);

      act(() => {
        result.current.setTheme('system');
      });

      expect(document.documentElement.classList.contains('dark')).toBe(false);
    });
  });

  // ----- OS change listener in system mode ---------------------------

  describe('OS theme change listener', () => {
    it('reacts to OS theme change when in system mode', () => {
      darkMatches = false;

      const { result } = renderHook(() => useTheme(), { wrapper });
      expect(result.current.theme).toBe('system');
      expect(document.documentElement.classList.contains('dark')).toBe(false);

      act(() => {
        simulateSystemThemeChange(true);
      });

      expect(document.documentElement.classList.contains('dark')).toBe(true);
    });

    it('does not react to OS change when theme is explicitly set', () => {
      darkMatches = false;

      const { result } = renderHook(() => useTheme(), { wrapper });

      act(() => {
        result.current.setTheme('light');
      });

      act(() => {
        simulateSystemThemeChange(true);
      });

      expect(document.documentElement.classList.contains('dark')).toBe(false);
    });
  });

  // ----- useTheme outside provider -----------------------------------

  describe('useTheme outside ThemeProvider', () => {
    it('throws when used outside ThemeProvider', () => {
      expect(() => {
        renderHook(() => useTheme());
      }).toThrow('useTheme must be used within a ThemeProvider');
    });
  });
});

/* ------------------------------------------------------------------ */
/*  Anti-FOUC script (layout.tsx)                                     */
/* ------------------------------------------------------------------ */

describe('Anti-FOUC script', () => {
  it('adds dark class when localStorage has dark', () => {
    document.documentElement.classList.remove('dark');
    const store: Record<string, string> = { theme: 'dark' };
    const getItem = (key: string) => store[key] ?? null;

    const script = `
      (function(){
        try {
          var t = localStorage.getItem('theme');
          var d = (t === 'dark') ||
                  (!t && window.matchMedia('(prefers-color-scheme: dark)').matches);
          if (d) document.documentElement.classList.add('dark');
        } catch(e) {}
      })();
    `;

    const origGetItem = window.localStorage.getItem;
    window.localStorage.getItem = vi.fn(getItem) as typeof window.localStorage.getItem;

    // eslint-disable-next-line no-eval
    eval(script);

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    window.localStorage.getItem = origGetItem;
  });

  it('adds dark class when no localStorage but OS prefers dark', () => {
    document.documentElement.classList.remove('dark');
    darkMatches = true;

    const origGetItem = window.localStorage.getItem;
    window.localStorage.getItem = vi.fn(() => null) as typeof window.localStorage.getItem;

    const script = `
      (function(){
        try {
          var t = localStorage.getItem('theme');
          var d = (t === 'dark') ||
                  (!t && window.matchMedia('(prefers-color-scheme: dark)').matches);
          if (d) document.documentElement.classList.add('dark');
        } catch(e) {}
      })();
    `;

    // eslint-disable-next-line no-eval
    eval(script);

    expect(document.documentElement.classList.contains('dark')).toBe(true);
    window.localStorage.getItem = origGetItem;
  });

  it('does not add dark class for light preference with no localStorage', () => {
    document.documentElement.classList.remove('dark');
    darkMatches = false;

    const origGetItem = window.localStorage.getItem;
    window.localStorage.getItem = vi.fn(() => null) as typeof window.localStorage.getItem;

    const script = `
      (function(){
        try {
          var t = localStorage.getItem('theme');
          var d = (t === 'dark') ||
                  (!t && window.matchMedia('(prefers-color-scheme: dark)').matches);
          if (d) document.documentElement.classList.add('dark');
        } catch(e) {}
      })();
    `;

    // eslint-disable-next-line no-eval
    eval(script);

    expect(document.documentElement.classList.contains('dark')).toBe(false);
    window.localStorage.getItem = origGetItem;
  });
});
