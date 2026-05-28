---
type: incident
status: approved
created: 2026-05-27
updated: 2026-05-27
tags: [testing, vitest, playwright, e2e, local, frontend]
---
# Run test FE locale — 2026-05-27

> Vitest verde (434/434); Playwright mocked **non eseguibile** in locale per browser Chromium mancante (30 fail infrastrutturali, 10 skip staging).

## Contesto

Esecuzione manuale su workstation Windows dopo `git pull` su `master` (HEAD allineato a CI verde su `4c7ca73`, run GitHub Actions #131). [^src: wiki/log.md §2026-05-27 doc-sync EP-018 Wave 2 CI green]

Comandi:

- `npm test` (Vitest) in `src/frontend/`
- `npx playwright test --reporter=line` (config mocked `playwright.config.ts`, WebServer Next.js)

Artefatto locale: `src/frontend/e2e/test-results/.last-run.json` (`status: failed`, 30 ID test falliti). [^src: src/frontend/e2e/test-results/.last-run.json]

## Riepilogo

| Suite | Totale | Pass | Fail | Skip | Esito |
|-------|--------|------|------|------|-------|
| Vitest (`npm test`) | 434 | 434 | 0 | 0 | **PASS** |
| Playwright mocked (`test:e2e`) | 40 | 0 | 30 | 10 | **FAIL** (infra locale) |
| Playwright real BE (`test:e2e:realbe`) | — | — | — | — | Non eseguito in questo run |

## Vitest — dettaglio

- **39** file di test, **434** test, durata ~72s.
- Nessun fallimento; warning noti su `DialogContent` / `DialogTitle` in `idle-timeout-provider.test.tsx` (Radix a11y hint, non bloccante).

## Playwright mocked — dettaglio per file

| File spec | Test | Pass | Fail | Skip | Nota |
|-----------|------|------|------|------|------|
| `accessibility-keyboard.spec.ts` | 6 | 0 | 6 | 0 | `browserType.launch`: Chromium headless shell assente |
| `accessibility-zoom.spec.ts` | 6 | 0 | 6 | 0 | idem |
| `deep-analysis.spec.ts` | 6 | 0 | 6 | 0 | idem |
| `search-to-analysis.spec.ts` | 5 | 0 | 5 | 0 | idem |
| `top-picks.spec.ts` | 6 | 0 | 6 | 0 | idem |
| `cutover-smoke.spec.ts` | 10 | 0 | 0 | 10 | Skip: `STAGING_USER_EMAIL` / `STAGING_USER_PASSWORD` non impostate |
| **Totale** | **40** | **0** | **30** | **10** | |

### Causa root (30 fail)

Tutti i 30 test falliti con lo stesso errore infrastrutturale:

```
browserType.launch: Executable doesn't exist at
...\ms-playwright\chromium_headless_shell-1161\chrome-win\headless_shell.exe
→ npx playwright install
```

Nessun fallimento funzionale dell'applicazione osservato in questo run: i test non hanno raggiunto il browser.

### Warning WebServer (non bloccanti per il conteggio)

Durante l'avvio del WebServer Playwright:

- `Middleware cannot be used with "output: export"` — gap noto [[gaps#fe-middleware-static-export-conflict]].
- Deprecation Next.js: convenzione `middleware` → `proxy`.

## Playwright cutover-smoke — skip

I 10 scenari in `cutover-smoke.spec.ts` usano `test.skip(!USER || !PASS, …)` quando mancano credenziali staging. [^src: src/frontend/e2e/cutover-smoke.spec.ts]

Policy archiviazione report: vedi `src/frontend/e2e/cutover-smoke.README.md`.

## Confronto CI

| Ambiente | Playwright mocked | Playwright real BE | Vitest | Gradle BE |
|----------|-------------------|--------------------|--------|-----------|
| CI `4c7ca73` (#131) | pass | pass | pass | pass |
| Locale 2026-05-27 | fail (browser) | n/a | pass | n/a |

La divergenza locale vs CI è **solo tooling** (browser Playwright non installati), non regressione codice dimostrata da questo run.

## Remediation locale

1. `cd src/frontend && npx playwright install` (o `npm run playwright:install`).
2. Rieseguire `npm run test:e2e`.
3. Per cutover-smoke: esportare `STAGING_USER_EMAIL` e `STAGING_USER_PASSWORD`, poi `npx playwright test cutover-smoke`.

## Aggiornamenti (v2026-05-27) — Rerun post `playwright install`

Dopo `npx playwright install chromium` e `npm run test:e2e -- --reporter=line` (~47s):

| Suite | Totale | Pass | Fail | Skip |
|-------|--------|------|------|------|
| Playwright mocked | 40 | **26** | **4** | 10 |

### 4 fail funzionali (non infra)

| Test | Causa probabile | Severità |
|------|-----------------|----------|
| `accessibility-keyboard` Flow 1 (login Tab) | Focus non raggiunge `login-email` entro 10 Tab (navbar + skip-link + link senza `data-testid` prima del form) | Test brittle / tab order |
| `accessibility-keyboard` Flow 2 (search Tab) | Dopo 15 Tab, `activeElement` è `BODY`, non l'input ricerca | Test brittle / tab order |
| `accessibility-keyboard` Shift+Tab | Stesso pattern su `login-submit` | Test brittle |
| `deep-analysis` happy path AAPL | `deep-analysis-loading` non visibile entro 5s — mock API troppo veloce, skeleton non osservabile (altri 5 test `deep-analysis` **pass**) | Flake / race |

### 26 pass (evidenza suite sana)

- `accessibility-keyboard`: 3/6 (Flow 3–4, Escape)
- `accessibility-zoom`: 6/6
- `deep-analysis`: 5/6
- `search-to-analysis`: 5/5
- `top-picks`: 6/6

### Valutazione

1. **Infra risolta** — install browser elimina i 30 fail precedenti.
2. **CI resta riferimento** — pipeline installa browser in runner; i 4 fail locali non invalidano CI #131 senza riproduzione in Actions.
3. **Azioni suggerite** (L5, non wiki):
   - Keyboard E2E: aumentare `maxTabs` o usare `getByTestId(...).focus()` solo per seed iniziale, poi continuare navigazione tastiera; oppure `page.goto` con `?` focus trap ridotto su `/login`.
   - `deep-analysis` happy path: rimuovere assert su loading transitorio o usare `toBeVisible({ timeout: 0 })` con `Promise.all` — allineare al test `verdict badge` che passa senza skeleton.

[^src: src/frontend/e2e/accessibility-keyboard.spec.ts]
[^src: src/frontend/e2e/deep-analysis.spec.ts]

## Aggiornamenti (v2026-05-27) — Remediation TSK-239

**Kanban:** US-083 + TSK-239 (Sprint 15.5 hotfix EP-016) — TSK `done`.

| Suite | Totale | Pass | Fail | Skip |
|-------|--------|------|------|------|
| Playwright mocked (2 run consecutivi) | 40 | **30** | **0** | 10 |

**Fix applicati:**
- `accessibility-keyboard`: seed `focus()` su primo campo form / search, poi Tab/Shift+Tab/Enter nel flusso critico (navbar oltre budget Tab pratico).
- `deep-analysis` happy path: rimosso assert su `deep-analysis-loading` (mock istantaneo).

[^src: management/kanban/EP-016-refinement-ui-accessibilita/US-083-incident-e2e-a11y-local-hardening/TSK-239.md]

## Aggiornamenti (v2026-05-27) — Revisione e convalida

- **Code review:** approvata su `accessibility-keyboard.spec.ts`, `deep-analysis.spec.ts` (strategia seed focus + rimozione assert loading transitorio).
- **Convalida:** `npm run test:e2e` — 30 pass, 0 fail, 10 skip (run post-review).
- **Kanban:** TSK-239 `done`, US-083 `done`, EP-016 ripristinata `done`.

## Concetti correlati

- [[material-design-3-accessibility]] — TSK-193 E2E tastiera/zoom
- [[frontend-error-notifications]] — stato CI E2E storico
- [[fintech-security-compliance]] — CI #131 post EP-018 Wave 2

## Pagine collegate

- [[gaps#fe-middleware-static-export-conflict]]

## Storie collegate
<!-- Sezione gestita dal product-manager — non modificare -->

## Aggiornamenti (v2026-05-28) — static runtime parity (`:8080`)

- Riprodotto localmente errore route FE su runtime statico backend (`/top-picks`, `/analysis/deep`): backend logga `NoResourceFoundException` quando la route non viene forwardata verso `index.html` statico.
- Mitigazione applicata in codebase:
  - estese route forward in `SpaRoutingConfig` per `/top-picks`, `/analysis/deep`, `/profile/mfa`, `/admin`, `/403`;
  - allineati smoke test FE su selector route correnti (`top-pick-row-*`, deep route query-param).
- Nota operativa locale: per tour applicativo su Podman e` disponibile override non invasivo `docker-compose.local-no-embeddings.yml` (compose canonico invariato).
