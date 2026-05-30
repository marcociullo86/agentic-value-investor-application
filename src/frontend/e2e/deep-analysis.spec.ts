/**
 * E2E — Deep Analysis page (async run/latest flow).
 *
 * Mocking strategy:
 *  - All scenarios use `page.route()` to intercept API calls and return
 *    deterministic JSON fixtures. The BE is NOT started in CI.
 *    Consistent with the pattern established in search-to-analysis.spec.ts
 *    (TSK-022) using Playwright network mocking.
 *  - The new backend exposes:
 *      POST /api/analysis/{ticker}/deep/runs?invoke_llm=…   → 202 DeepAnalysisRunDto
 *      GET  /api/analysis/{ticker}/deep/latest               → 200 LatestDeepAnalysis
 *    Each test installs its own GET handler whose status transitions over
 *    successive calls (typically RUNNING → SUCCESS).
 *
 * Selectors:
 *  - Mix of semantic (`getByRole`, `getByText`) + `data-testid` already
 *    present in the page and the 5 deep components.
 *
 * Scenarios covered:
 *  - Latest SUCCESS at first paint → render full result (no auto-rerun).
 *  - Latest NONE → empty state with hint to press "Analizza".
 *  - Manual run → POST runs + GET latest sequence RUNNING → SUCCESS.
 *  - Latest FAILED with reason not_found (404 equivalent) → error panel.
 *  - Latest FAILED with reason no_sec_filings → dedicated message.
 *  - Latest FAILED with reason not_indexed → hint + ingest CTA.
 *  - Buttons disabled while RUNNING (double-click guard, FE side).
 *  - 3-button manual run bar (Indicizza filing, Analizza, Analizza + LLM).
 *  - Ingest run → POST /deep/ingest + ingest/latest RUNNING → SUCCESS with
 *    status line updated to "Ultima indicizzazione …  · N filing".
 *  - Page title contains ticker.
 *  - Accessibility: aria-label on verdict badge.
 */

import { test, expect } from '@playwright/test';
import type { Page, Route } from '@playwright/test';

// eslint-disable-next-line @typescript-eslint/no-require-imports
const deepAaplFixture = require('./fixtures/deep-analysis-aapl.json') as Record<string, unknown>;
// eslint-disable-next-line @typescript-eslint/no-require-imports
const deepValueTrapFixture = require('./fixtures/deep-analysis-value-trap.json') as Record<string, unknown>;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

interface LatestPayload {
  ticker: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NONE';
  runId: string | null;
  invokeLlm: boolean;
  requestedAt: string | null;
  completedAt: string | null;
  result: Record<string, unknown> | null;
  error: { reason: string; message: string | null } | null;
}

interface IngestSummary {
  filingsTotal: number;
  chunksIndexed: number;
  chunksSkipped: number;
  indexedAt: string | null;
}

interface IngestLatestPayload {
  ticker: string;
  status: 'RUNNING' | 'SUCCESS' | 'FAILED' | 'NONE';
  runId: string | null;
  requestedAt: string | null;
  completedAt: string | null;
  summary: IngestSummary | null;
  error: { reason: string; message: string | null } | null;
}

function successPayload(
  ticker: string,
  result: Record<string, unknown>,
  invokeLlm = false,
): LatestPayload {
  return {
    ticker,
    status: 'SUCCESS',
    runId: 'run-success-1',
    invokeLlm,
    requestedAt: '2026-05-22T09:59:30Z',
    completedAt: '2026-05-22T10:00:00Z',
    result,
    error: null,
  };
}

function runningPayload(ticker: string, invokeLlm = false): LatestPayload {
  return {
    ticker,
    status: 'RUNNING',
    runId: 'run-running-1',
    invokeLlm,
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: null,
    result: null,
    error: null,
  };
}

function failedPayload(
  ticker: string,
  reason: string,
  message: string | null,
  invokeLlm = false,
): LatestPayload {
  return {
    ticker,
    status: 'FAILED',
    runId: 'run-failed-1',
    invokeLlm,
    requestedAt: '2026-05-22T09:59:30Z',
    completedAt: '2026-05-22T09:59:45Z',
    result: null,
    error: { reason, message },
  };
}

function nonePayload(ticker: string): LatestPayload {
  return {
    ticker,
    status: 'NONE',
    runId: null,
    invokeLlm: false,
    requestedAt: null,
    completedAt: null,
    result: null,
    error: null,
  };
}

async function mockLatestSequence(
  page: Page,
  ticker: string,
  sequence: readonly LatestPayload[],
): Promise<{ readonly getCalls: () => number }> {
  let i = 0;
  await page.route(`**/api/analysis/${ticker}/deep/latest`, (route: Route) => {
    const idx = Math.min(i, sequence.length - 1);
    const payload = sequence[idx] ?? sequence[sequence.length - 1];
    i++;
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(payload),
    });
  });
  return { getCalls: (): number => i };
}

async function mockStartRun(
  page: Page,
  ticker: string,
): Promise<{ readonly getCount: () => number }> {
  let postCount = 0;
  await page.route(
    `**/api/analysis/${ticker}/deep/runs**`,
    (route: Route) => {
      postCount++;
      return route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          runId: `run-${postCount}`,
          ticker,
          status: 'RUNNING',
          invokeLlm: route.request().url().includes('invoke_llm=true'),
        }),
      });
    },
  );
  return { getCount: (): number => postCount };
}

function ingestNonePayload(ticker: string): IngestLatestPayload {
  return {
    ticker,
    status: 'NONE',
    runId: null,
    requestedAt: null,
    completedAt: null,
    summary: null,
    error: null,
  };
}

function ingestRunningPayload(ticker: string): IngestLatestPayload {
  return {
    ticker,
    status: 'RUNNING',
    runId: 'ing-run-1',
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: null,
    summary: null,
    error: null,
  };
}

function ingestSuccessPayload(ticker: string): IngestLatestPayload {
  return {
    ticker,
    status: 'SUCCESS',
    runId: 'ing-run-1',
    requestedAt: '2026-05-22T09:59:55Z',
    completedAt: '2026-05-22T10:00:30Z',
    summary: {
      filingsTotal: 12,
      chunksIndexed: 480,
      chunksSkipped: 3,
      indexedAt: '2026-05-22T10:00:30Z',
    },
    error: null,
  };
}

async function mockIngestLatestSequence(
  page: Page,
  ticker: string,
  sequence: readonly IngestLatestPayload[],
): Promise<{ readonly getCalls: () => number }> {
  let i = 0;
  await page.route(
    `**/api/analysis/${ticker}/deep/ingest/latest`,
    (route: Route) => {
      const idx = Math.min(i, sequence.length - 1);
      const payload = sequence[idx] ?? sequence[sequence.length - 1];
      i++;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(payload),
      });
    },
  );
  return { getCalls: (): number => i };
}

async function mockStartIngest(
  page: Page,
  ticker: string,
): Promise<{ readonly getCount: () => number }> {
  let postCount = 0;
  await page.route(
    `**/api/analysis/${ticker}/deep/ingest`,
    (route: Route) => {
      if (route.request().method() !== 'POST') {
        return route.fallback();
      }
      postCount++;
      return route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          runId: `ing-${postCount}`,
          ticker,
          status: 'RUNNING',
          invokeLlm: false,
          kind: 'INGEST',
        }),
      });
    },
  );
  return { getCount: (): number => postCount };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe('Deep Analysis page (async flow)', () => {
  test.beforeEach(async ({ page }) => {
    // Default ingest stub: every test gets an NONE ingest snapshot unless it
    // installs a more specific handler before this one. The route registered
    // here is checked AFTER per-test routes (last-registered wins in Playwright),
    // so per-test handlers always take precedence.
    await page.route(
      /\/api\/analysis\/[^/]+\/deep\/ingest\/latest/,
      (route) => {
        const m = route.request().url().match(/\/analysis\/([^/]+)\/deep/);
        const ticker = m?.[1] ?? 'AAPL';
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(ingestNonePayload(ticker)),
        });
      },
    );
  });

  test('happy path AAPL — latest SUCCESS renders verdict + 5 sections', async ({ page }) => {
    await mockLatestSequence(page, 'AAPL', [
      successPayload('AAPL', deepAaplFixture),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });
    await expect(verdictBadge).toContainText(/APPROVATO/);

    await expect(page.getByTestId('deep-verdict-section')).toBeVisible();
    await expect(page.getByTestId('munger-report-section')).toBeVisible();
    await expect(page.getByTestId('news-sentiment-section')).toBeVisible();
    await expect(page.getByTestId('drawdown-chart-section')).toBeVisible();
    await expect(page.getByTestId('edgar-filing-section')).toBeVisible();

    // No running banner when latest already SUCCESS.
    await expect(page.getByTestId('deep-analysis-running')).toHaveCount(0);
  });

  test('verdict badge has aria-label for accessibility', async ({ page }) => {
    await mockLatestSequence(page, 'AAPL', [
      successPayload('AAPL', deepAaplFixture),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });

    const ariaLabel = await verdictBadge.getAttribute('aria-label');
    expect(ariaLabel).toBeTruthy();
    expect(ariaLabel).toContain('Verdetto');
    expect(ariaLabel).toContain('Approvato');
  });

  test('value-trap scenario — badge shows BOCCIATO VALUE TRAP', async ({ page }) => {
    await mockLatestSequence(page, 'TRAP', [
      successPayload('TRAP', deepValueTrapFixture),
    ]);

    await page.goto('/analysis/deep?ticker=TRAP');

    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 15_000 });
    await expect(verdictBadge).toContainText(/BOCCIATO VALUE TRAP/);

    const ariaLabel = await verdictBadge.getAttribute('aria-label');
    expect(ariaLabel).toContain('Bocciato Value Trap');
  });

  test('latest NONE — shows empty state and no auto-run', async ({ page }) => {
    const runs = await mockStartRun(page, 'NEW');
    await mockLatestSequence(page, 'NEW', [nonePayload('NEW')]);

    await page.goto('/analysis/deep?ticker=NEW');

    await expect(page.getByTestId('deep-analysis-empty')).toBeVisible({
      timeout: 15_000,
    });
    // Empty state must not trigger any POST runs.
    expect(runs.getCount()).toBe(0);

    // Manual run buttons remain enabled in NONE state.
    await expect(page.getByTestId('deep-analysis-manual-run')).toBeEnabled();
    await expect(page.getByTestId('deep-analysis-manual-run-llm')).toBeEnabled();
  });

  test('latest FAILED not_found — shows "Ticker non trovato" and search link', async ({ page }) => {
    await mockLatestSequence(page, 'XYZINVALID', [
      failedPayload('XYZINVALID', 'NOT_FOUND', 'Ticker not found'),
    ]);

    await page.goto('/analysis/deep?ticker=XYZINVALID');

    const errorPanel = page.getByTestId('deep-analysis-error');
    await expect(errorPanel).toBeVisible({ timeout: 15_000 });
    await expect(errorPanel).toContainText(/Ticker non trovato/);

    const searchLink = errorPanel.getByRole('link', {
      name: /cerca un altro ticker/i,
    });
    await expect(searchLink).toBeVisible();
    await expect(searchLink).toHaveAttribute('href', /\/screener\/?/);
  });

  test('latest FAILED no_sec_filings — shows dedicated message', async ({ page }) => {
    await mockLatestSequence(page, 'NOSEC', [
      failedPayload('NOSEC', 'NO_SEC_FILINGS', 'No 10-K or 10-Q filings'),
    ]);

    await page.goto('/analysis/deep?ticker=NOSEC');

    const errorPanel = page.getByTestId('deep-analysis-error');
    await expect(errorPanel).toBeVisible({ timeout: 15_000 });
    await expect(errorPanel).toContainText(/Nessun filing SEC disponibile/);
  });

  test('manual run — POST runs then poll latest RUNNING → SUCCESS', async ({ page }) => {
    const runs = await mockStartRun(page, 'AAPL');
    await mockLatestSequence(page, 'AAPL', [
      // initial paint: no previous execution
      nonePayload('AAPL'),
      // first poll after POST: still running
      runningPayload('AAPL', false),
      // subsequent polls: done
      successPayload('AAPL', deepAaplFixture),
      successPayload('AAPL', deepAaplFixture),
      successPayload('AAPL', deepAaplFixture),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    // Empty state first.
    await expect(page.getByTestId('deep-analysis-empty')).toBeVisible({
      timeout: 15_000,
    });

    const runBtn = page.getByTestId('deep-analysis-manual-run');
    await expect(runBtn).toBeEnabled();
    await runBtn.click();

    // Exactly one POST.
    await expect.poll(() => runs.getCount(), { timeout: 5_000 }).toBe(1);

    // Running banner becomes visible while latest is RUNNING.
    await expect(page.getByTestId('deep-analysis-running')).toBeVisible({
      timeout: 15_000,
    });

    // Both run buttons disabled while RUNNING.
    await expect(page.getByTestId('deep-analysis-manual-run')).toBeDisabled();
    await expect(
      page.getByTestId('deep-analysis-manual-run-llm'),
    ).toBeDisabled();

    // After polling cycles (3s interval), latest flips to SUCCESS and the
    // verdict badge is rendered.
    const verdictBadge = page.getByTestId('verdict-badge');
    await expect(verdictBadge).toBeVisible({ timeout: 30_000 });
    await expect(verdictBadge).toContainText(/APPROVATO/);

    // Running banner disappears on terminal status.
    await expect(page.getByTestId('deep-analysis-running')).toHaveCount(0);
  });

  test('manual run with LLM — POST runs?invoke_llm=true', async ({ page }) => {
    let postUrl = '';
    await page.route('**/api/analysis/AAPL/deep/runs**', (route: Route) => {
      postUrl = route.request().url();
      return route.fulfill({
        status: 202,
        contentType: 'application/json',
        body: JSON.stringify({
          runId: 'run-llm-1',
          ticker: 'AAPL',
          status: 'RUNNING',
          invokeLlm: true,
        }),
      });
    });
    await mockLatestSequence(page, 'AAPL', [
      nonePayload('AAPL'),
      successPayload('AAPL', deepAaplFixture, true),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    await expect(page.getByTestId('deep-analysis-empty')).toBeVisible({
      timeout: 15_000,
    });

    await page.getByTestId('deep-analysis-manual-run-llm').click();

    await expect
      .poll(() => postUrl, { timeout: 5_000 })
      .toMatch(/invoke_llm=true/);

    // Eventually SUCCESS renders.
    await expect(page.getByTestId('verdict-badge')).toBeVisible({
      timeout: 30_000,
    });
  });

  test('latest RUNNING at first paint — banner visible and polling continues', async ({ page }) => {
    await mockLatestSequence(page, 'AAPL', [
      runningPayload('AAPL', false),
      runningPayload('AAPL', false),
      successPayload('AAPL', deepAaplFixture),
      successPayload('AAPL', deepAaplFixture),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    await expect(page.getByTestId('deep-analysis-running')).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByTestId('deep-analysis-manual-run')).toBeDisabled();

    // Polling eventually resolves to SUCCESS.
    await expect(page.getByTestId('verdict-badge')).toBeVisible({
      timeout: 30_000,
    });
  });

  test('page title displays the ticker', async ({ page }) => {
    await mockLatestSequence(page, 'AAPL', [
      successPayload('AAPL', deepAaplFixture),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    const title = page.getByTestId('deep-analysis-title');
    await expect(title).toBeVisible({ timeout: 15_000 });
    await expect(title).toContainText('AAPL');
    await expect(title).toContainText('Deep Analysis');
  });

  test('manual run bar exposes 3 buttons (Indicizza filing, Analizza, Analizza + LLM)', async ({
    page,
  }) => {
    await mockLatestSequence(page, 'AAPL', [nonePayload('AAPL')]);

    await page.goto('/analysis/deep?ticker=AAPL');

    const ingestBtn = page.getByTestId('deep-analysis-ingest-run');
    const runBtn = page.getByTestId('deep-analysis-manual-run');
    const runLlmBtn = page.getByTestId('deep-analysis-manual-run-llm');

    await expect(ingestBtn).toBeVisible({ timeout: 15_000 });
    await expect(runBtn).toBeVisible();
    await expect(runLlmBtn).toBeVisible();

    await expect(ingestBtn).toHaveText(/Indicizza filing/);
    await expect(runBtn).toHaveText(/Analizza$/);
    await expect(runLlmBtn).toHaveText(/Analizza \+ LLM/);

    // NONE ingest → status line shows "Mai indicizzato".
    await expect(page.getByTestId('deep-analysis-ingest-status')).toContainText(
      /Mai indicizzato/,
    );
  });

  test('ingest run — POST /deep/ingest then poll RUNNING → SUCCESS, status line updates', async ({
    page,
  }) => {
    await mockLatestSequence(page, 'AAPL', [nonePayload('AAPL')]);
    const ingestPosts = await mockStartIngest(page, 'AAPL');
    await mockIngestLatestSequence(page, 'AAPL', [
      ingestNonePayload('AAPL'),
      ingestRunningPayload('AAPL'),
      ingestSuccessPayload('AAPL'),
      ingestSuccessPayload('AAPL'),
      ingestSuccessPayload('AAPL'),
    ]);

    await page.goto('/analysis/deep?ticker=AAPL');

    const ingestBtn = page.getByTestId('deep-analysis-ingest-run');
    await expect(ingestBtn).toBeEnabled({ timeout: 15_000 });

    // Initial status line "Mai indicizzato".
    await expect(page.getByTestId('deep-analysis-ingest-status')).toContainText(
      /Mai indicizzato/,
    );

    await ingestBtn.click();

    await expect.poll(() => ingestPosts.getCount(), { timeout: 5_000 }).toBe(1);

    // Running banner specific to ingest, button disabled while RUNNING.
    await expect(page.getByTestId('deep-analysis-ingest-running')).toBeVisible({
      timeout: 15_000,
    });
    await expect(ingestBtn).toBeDisabled();

    // After polling cycles, status flips to SUCCESS: banner gone, status line
    // shows "Ultima indicizzazione …  · 12 filing".
    await expect(page.getByTestId('deep-analysis-ingest-running')).toHaveCount(
      0,
      { timeout: 30_000 },
    );
    await expect(page.getByTestId('deep-analysis-ingest-status')).toContainText(
      /Ultima indicizzazione: .* · 12 filing/,
      { timeout: 30_000 },
    );
  });

  test('analysis FAILED not_indexed — error panel shows hint + ingest CTA', async ({
    page,
  }) => {
    await mockLatestSequence(page, 'NEWTICK', [
      failedPayload('NEWTICK', 'not_indexed', 'Filings non indicizzati'),
    ]);
    const ingestPosts = await mockStartIngest(page, 'NEWTICK');
    await mockIngestLatestSequence(page, 'NEWTICK', [
      ingestNonePayload('NEWTICK'),
    ]);

    await page.goto('/analysis/deep?ticker=NEWTICK');

    const errorPanel = page.getByTestId('deep-analysis-error');
    await expect(errorPanel).toBeVisible({ timeout: 15_000 });
    await expect(errorPanel).toContainText(/Indicizza prima i filing/);

    const cta = page.getByTestId('deep-analysis-error-ingest-cta');
    await expect(cta).toBeVisible();
    await expect(cta).toBeEnabled();
    await cta.click();

    await expect.poll(() => ingestPosts.getCount(), { timeout: 5_000 }).toBe(1);
  });
});
