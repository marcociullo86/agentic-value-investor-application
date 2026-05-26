import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import { render, screen } from '@testing-library/react';
import { axe } from 'vitest-axe';
import { toHaveNoViolations } from 'vitest-axe/matchers';
import type { ReactNode } from 'react';

expect.extend({ toHaveNoViolations });

/* ------------------------------------------------------------------ */
/*  Global polyfills for jsdom                                        */
/* ------------------------------------------------------------------ */

beforeAll(() => {
  Element.prototype.scrollIntoView = vi.fn();
  Element.prototype.hasPointerCapture ??= () => false;
  Element.prototype.setPointerCapture ??= () => {};
  Element.prototype.releasePointerCapture ??= () => {};
});

/* ------------------------------------------------------------------ */
/*  Mocks — Next.js navigation                                        */
/* ------------------------------------------------------------------ */

const pushMock = vi.fn();
const replaceMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: replaceMock }),
  useSearchParams: () => new URLSearchParams('ticker=AAPL'),
  usePathname: () => '/top-picks',
  useParams: () => ({ ticker: 'AAPL' }),
}));

vi.mock('next/link', () => ({
  __esModule: true,
  default: ({ children, href, ...props }: { children: ReactNode; href: string; [k: string]: unknown }) => (
    <a href={href} {...props}>{children}</a>
  ),
}));

/* ------------------------------------------------------------------ */
/*  Mocks — Stores                                                    */
/* ------------------------------------------------------------------ */

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      login: vi.fn(),
      token: 'mock-token',
      user: { email: 'test@example.com', displayName: 'Test' },
      isAuthenticated: true,
      logout: vi.fn(),
      checkAuth: vi.fn(),
      sessionExpired: false,
    }),
}));

vi.mock('@/lib/stores/useWatchlistStore', () => ({
  useWatchlistStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      items: [
        { ticker: 'AAPL', addedAt: '2026-05-01T00:00:00Z' },
        { ticker: 'MSFT', addedAt: '2026-05-02T00:00:00Z' },
      ],
      loading: false,
      error: null,
      fetch: vi.fn(),
      add: vi.fn(),
      remove: vi.fn(),
    }),
}));

vi.mock('@/lib/stores/useAnalysisStore', () => ({
  useAnalysisStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      byTicker: {
        AAPL: {
          grahamNumber: 120,
          dcfIntrinsicValue: 145,
          dcfMethod: 'fcfe',
          dcfMethodSource: 'auto',
          mosSignal: 'GREEN',
          currentPriceAtEval: 180,
          dataSnapshotAt: '2026-05-20T10:00:00Z',
          isStale: false,
          signals: [
            { ruleId: 'profitability.roe', signal: 'GREEN', observedValue: 18, threshold: 'ROE ≥ 15%' },
            { ruleId: 'solvency.debt_equity', signal: 'GREEN', observedValue: 0.5, threshold: 'D/E ≤ 0.5' },
          ],
          contextFlags: null,
        },
      },
      loading: {},
      errors: {},
      fetchAnalysis: vi.fn(),
    }),
}));

vi.mock('@/lib/hooks/useHistorical', () => ({
  useHistorical: () => ({
    data: {
      points: [
        { year: 2020, revenue: 100, netIncome: 20 },
        { year: 2021, revenue: 120, netIncome: 25 },
      ],
      dataSnapshotAt: '2026-05-20T10:00:00Z',
    },
    loading: false,
  }),
}));

vi.mock('@/lib/hooks/useTopPicks', () => ({
  useTopPicks: () => ({
    data: {
      items: [
        {
          ticker: 'AAPL',
          rankPosition: 1,
          verdettoClasse: 'APPROVATO',
          marginOfSafety: 25,
          sector: 'Technology',
          marketCapUsd: 3000000000000,
          source: 'daily-batch',
          companyName: 'Apple Inc.',
        },
        {
          ticker: 'MSFT',
          rankPosition: 2,
          verdettoClasse: 'WATCHLIST',
          marginOfSafety: 10,
          sector: 'Technology',
          marketCapUsd: 2800000000000,
          source: 'daily-batch',
          companyName: 'Microsoft Corp.',
        },
      ],
      page: 0,
      size: 30,
      total: 2,
      runDate: '2026-05-26',
    },
    error: undefined,
    isLoading: false,
    isValidating: false,
    mutate: vi.fn(),
  }),
}));

vi.mock('@/lib/api/auth', () => ({
  register: vi.fn(),
}));

vi.mock('@/lib/api/search', () => ({
  searchTicker: vi.fn().mockResolvedValue({ items: [] }),
  normalizeTicker: (t: string) => t.toUpperCase().trim(),
}));

vi.mock('@/lib/api/top-picks', () => ({
  buildTopPicksUrl: () => '/api/top-picks',
  getTopPicks: vi.fn(),
}));

vi.mock('@/components/auth/AuthGuard', () => ({
  AuthGuard: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('@/components/auth/SessionExpiredBanner', () => ({
  SessionExpiredBanner: () => null,
}));

vi.mock('@/components/notifications/notification-provider', () => ({
  NotificationProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('@/components/notifications/notification-container', () => ({
  NotificationContainer: () => null,
}));

vi.mock('@/components/providers/AuthProvider', () => ({
  AuthProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('@/components/providers/ToastProvider', () => ({
  ToastProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('@/components/theme/theme-provider', () => ({
  ThemeProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
}));

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: ReactNode }) => <div>{children}</div>,
  LineChart: ({ children }: { children: ReactNode }) => <svg role="img" aria-label="Chart">{children}</svg>,
  Line: () => null,
  XAxis: () => null,
  YAxis: () => null,
  CartesianGrid: () => null,
  Tooltip: () => null,
  Legend: () => null,
}));

vi.mock('swr', () => ({
  __esModule: true,
  default: () => ({ data: undefined, error: undefined, isLoading: false, isValidating: false, mutate: vi.fn() }),
}));

/* ------------------------------------------------------------------ */
/*  Imports — Pages under test                                        */
/* ------------------------------------------------------------------ */

import LoginPage from '@/app/(auth)/login/page';
import RegisterPage from '@/app/(auth)/register/page';
import HomePage from '@/app/page';
import AnalysisPage from '@/app/analysis/page';
import WatchlistPage from '@/app/watchlist/page';
import ScreenerPage from '@/app/screener/page';
import { TopPicksPageClient } from '@/app/top-picks/TopPicksPageClient';

/* ------------------------------------------------------------------ */
/*  Helpers                                                            */
/* ------------------------------------------------------------------ */

beforeEach(() => {
  vi.clearAllMocks();
});

interface ViewTestCase {
  name: string;
  component: () => React.ReactElement;
}

const views: ViewTestCase[] = [
  { name: 'Login', component: () => <LoginPage /> },
  { name: 'Register', component: () => <RegisterPage /> },
  { name: 'Home', component: () => <HomePage /> },
  { name: 'Analysis', component: () => <AnalysisPage /> },
  { name: 'Top Picks', component: () => <TopPicksPageClient /> },
  { name: 'Watchlist', component: () => <WatchlistPage /> },
  { name: 'Screener', component: () => <ScreenerPage /> },
];

/* ------------------------------------------------------------------ */
/*  1. axe-core audit: zero serious/critical                          */
/* ------------------------------------------------------------------ */

describe('US-072 AC — axe-core: zero serious/critical violations', () => {
  it.each(views)(
    '$name page has no serious or critical axe violations',
    async ({ component }) => {
      const { container } = render(component());

      const results = await axe(container, {
        rules: {
          'color-contrast': { enabled: false },
        },
      });

      const seriousOrCritical = results.violations.filter(
        (v) => v.impact === 'serious' || v.impact === 'critical',
      );

      if (seriousOrCritical.length > 0) {
        const summary = seriousOrCritical.map(
          (v) => `[${v.impact}] ${v.id}: ${v.help} (${v.nodes.length} nodes)`,
        );
        expect(seriousOrCritical, summary.join('\n')).toHaveLength(0);
      }
      expect(seriousOrCritical).toHaveLength(0);
    },
  );
});

/* ------------------------------------------------------------------ */
/*  2. Lint h1: exactly one h1 per page                               */
/* ------------------------------------------------------------------ */

describe('US-072 AC — Single h1 per page', () => {
  it.each(views)(
    '$name page renders exactly one h1',
    async ({ component }) => {
      render(component());

      const headings = screen.getAllByRole('heading', { level: 1 });
      expect(headings).toHaveLength(1);
    },
  );
});

/* ------------------------------------------------------------------ */
/*  3. axe full audit: no violations at all (informational)           */
/* ------------------------------------------------------------------ */

describe('US-072 AC — axe-core full audit (all severities)', () => {
  it.each(views)(
    '$name page passes full axe audit (excluding color-contrast)',
    async ({ component }) => {
      const { container } = render(component());

      const results = await axe(container, {
        rules: {
          'color-contrast': { enabled: false },
        },
      });

      expect(results).toHaveNoViolations();
    },
  );
});
