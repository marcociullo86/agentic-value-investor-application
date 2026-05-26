/**
 * Lighthouse CI configuration — TSK-192 (US-072).
 *
 * Target: Accessibility score >= 95 on all main views.
 *
 * Prerequisites for CI execution:
 *   - `npm run build && npm run start` (or a preview server)
 *   - @lhci/cli installed: `npm i -D @lhci/cli`
 *   - Run: `npx lhci autorun`
 *
 * In environments without a headless browser (e.g. jsdom-only CI),
 * this config serves as documentation and is ready for use once
 * Playwright or a Chromium-based runner is available.
 */
module.exports = {
  ci: {
    collect: {
      startServerCommand: 'npm run start',
      startServerReadyPattern: 'ready on',
      startServerReadyTimeout: 30000,
      url: [
        'http://localhost:3000/',
        'http://localhost:3000/login',
        'http://localhost:3000/register',
        'http://localhost:3000/analysis?ticker=AAPL',
        'http://localhost:3000/top-picks',
        'http://localhost:3000/watchlist',
        'http://localhost:3000/screener',
      ],
      numberOfRuns: 1,
      settings: {
        onlyCategories: ['accessibility'],
        chromeFlags: '--no-sandbox --headless',
      },
    },
    assert: {
      assertions: {
        'categories:accessibility': ['error', { minScore: 0.95 }],
      },
    },
    upload: {
      target: 'temporary-public-storage',
    },
  },
};
