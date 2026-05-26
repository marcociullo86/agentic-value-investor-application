# Cutover Smoke Spec — README

`cutover-smoke.spec.ts` is an **on-demand** E2E suite that runs against a
**live staging deployment**. It does not use `page.route()` mocks: every
request hits the real application.

## When to run

- Pre-cutover verification before go-live R1.1 (see `docs/deploy/cutover-checklist-r11.md`)
- After any major infra change (VM resize, nginx config change, DB restore)
- Ad-hoc regression check against staging

**Do NOT run in CI default pipeline** — the suite requires staging credentials
and a live backend. Add it to CI only in a dedicated `staging-smoke` workflow
triggered manually or on release tags.

## Prerequisites

1. Staging stack running (`docker-compose.prod.yml up` on staging VM)
2. `actuator/health` returns `{"status":"UP"}`
3. A QA user account already registered on staging (e.g. `qa@example.com`)
4. TLS certificate installed and nginx serving HTTPS

## Running the suite

```bash
STAGING_URL=https://staging.app.example.com \
STAGING_USER_EMAIL=qa@example.com \
STAGING_USER_PASSWORD=*** \
npx playwright test cutover-smoke
```

With a specific browser (default: chromium):

```bash
STAGING_URL=https://staging.app.example.com \
STAGING_USER_EMAIL=qa@example.com \
STAGING_USER_PASSWORD=*** \
npx playwright test cutover-smoke --project=chromium
```

With HTML report:

```bash
STAGING_URL=https://staging.app.example.com \
STAGING_USER_EMAIL=qa@example.com \
STAGING_USER_PASSWORD=*** \
npx playwright test cutover-smoke --reporter=html
npx playwright show-report
```

## Environment variables

| Variable | Required | Default | Description |
|---|---|---|---|
| `STAGING_URL` | no | `https://app-staging.example.com` | Base URL of staging deployment |
| `STAGING_USER_EMAIL` | YES | — | Email of QA user on staging |
| `STAGING_USER_PASSWORD` | YES | — | Password of QA user on staging |

If `STAGING_USER_EMAIL` or `STAGING_USER_PASSWORD` are not set, the entire
suite is skipped with message "STAGING_USER_EMAIL / STAGING_USER_PASSWORD not set".

## Scenarios (10 total)

| # | Scenario | What is verified |
|---|---|---|
| 1 | Healthcheck | `GET /actuator/health` → 200 + `{"status":"UP"}` |
| 2 | Home accessible | Page load < 5s, no 5xx, title contains "Value Investing" |
| 3 | Login flow | Form login → redirect home + auth UI visible |
| 4 | Analysis flow | `/analysis/AAPL` → 13 ruleSignals + DCF + MoS badge |
| 5 | Deep analysis flow | `/analysis/AAPL/deep` → DeepVerdictBadge + MungerReport + EdgarFilingLinks |
| 6 | Top Picks flow | `/top-picks` → ≥1 row OR empty-state placeholder |
| 7 | Watchlist flow | Add AAPL → reload → AAPL persists |
| 8 | API contract | `GET /api/analysis/AAPL` (auth) → 200 + signals[13] |
| 9 | TLS / HSTS | `Strict-Transport-Security` header present with `max-age` |
| 10 | No console errors | Zero JS `pageerror` events across home, analysis, top-picks |

## Interpreting results

- All 10 scenarios PASS → green light for go-live (record in cutover checklist)
- Any FAIL → investigate before cutover; do NOT proceed to DNS swap

## Archiving results

Save the Playwright HTML report under:

```
docs/deploy/cutover-smoke-results-YYYY-MM-DD/
```

and reference it in `docs/deploy/cutover-checklist-r11.md` row "Playwright smoke".

[^src: management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/TSK-066.md]
[^src: management/kanban/EP-008-deploy-operativita-produzione/US-028-checklist-cutover-r11/US-028.md §Acceptance Criteria]
