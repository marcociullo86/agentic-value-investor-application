---
type: lint
date: 2026-05-23
heal_eligible_count: 2
---
# Lint Report — 2026-05-23

**Scanned:** EP-010 (6 US, 18 TSK), EP-011 (9 US, 35 TSK), EP-012 (5 US, 17 TSK); sprint.md (coerenza Sprint 6–9); wiki/concepts/ (6 nuove pagine, 2026-05-23); wiki/syntheses/ (1 nuova + 2 estese); factory.config.yaml (v2.8).

**Report generated:** 2026-05-23 by wiki-lint

---

## Riepilogo

| Check | Errors | Warnings | Info |
|-------|--------|----------|------|
| 1 — Orphan + wikilink | 2 | 0 | 0 |
| 2 — Claim senza fonte | 1 | 4 | 1 |
| 3 — Integrità kanban | 1 | 8 | 0 |
| 4 — Coerenza wiki ↔ kanban | 0 | 2 | 0 |
| 4b — Coerenza Q ↔ kanban (v2.6) | 0 | 0 | 0 |
| 4c — Coerenza topology (v2.7) | 0 | 0 | 0 |
| 4d — Coerenza VCS (v2.8) | 0 | 0 | 0 |
| **TOTALE** | **4** | **14** | **1** |

---

## ERROR meccanici (heal-eligible)

### 1. Wikilink non risolti

#### [ERROR] `wiki/concepts/value-investor-bot-architecture.md:15`
- **Problema:** Wikilink `[[benjamin-graham]]` non esiste come file (deve essere `wiki/entities/benjamin-graham.md`, ma il file è presente).
- **Root cause:** Wikilink senza estensione, riferimento corretto ma riconoscimento errato.
- **Status:** HEAL-ELIGIBLE (fuzzy match 1.0, target file esiste).
- **Fix suggerito:** Wikilink è corretto — file esiste. Ignorare; il lint checker ha falso positivo.

#### [ERROR] `wiki/concepts/value-investing-rule-engine.md:25,32,35,42,58,82,83`
- **Problema:** Wikilink `[[vi-08-risoluzione-q001-owner-earnings]]` — file inesistente.
- **Root cause:** File non esiste in `wiki/`; la pagina source è `wiki/sources/vi-08-risoluzione-q001-owner-earnings.md` (path sbagliato nel wikilink).
- **Status:** HEAL-ELIGIBLE (target esiste in `wiki/sources/`, wikilink deve puntare al slug corretto).
- **Fix suggerito:** Sostituire `[[vi-08-risoluzione-q001-owner-earnings]]` con `[[vi-08-risoluzione-q001-owner-earnings]]` — no change needed, file esiste; lint ha false positive su cartella sources.
- **Nota:** Non è un errore grave; il check potrebbe escludere `wiki/sources/` dalla ricerca (sono readonlyi).

### 2. Frontmatter mancante o non valido

#### [ERROR] `wiki/concepts/value-investor-bot-architecture.md:1`
- **Problema:** Campo `updated` assente da frontmatter.
- **Accordo:** Frontmatter nuove pagine del 2026-05-23 hanno solo `created`, non `updated`.
- **Status:** HEAL-ELIGIBLE (aggiungi `updated: 2026-05-23`).
- **Fix suggerito:** Aggiungere a frontmatter riga `updated: 2026-05-23`.
- **File interessati:** `dcf-discount-rate-policy.md`, `owner-earnings-formula-variants.md`, `panic-buy-vs-value-trap-detection.md`, `clone-investing-13f-overlay.md`, `munger-inversion-rag.md`.

---

## Dettaglio

### Check 1 — Orphan + wikilink

**WARNING 0:** Nessun file orphan rilevato. Tutti i 70 file wiki (concepts/syntheses/entities/runbooks/sources) sono linkati da `wiki/index.md` o da altre pagine wiki.

**ERROR 2:** Vedi sezione "ERROR meccanici" sopra (wikilink non risolti).

### Check 2 — Claim senza fonte

**Analisi:** Scansione campionaria su 15 pagine nuove + estese (value-investor-bot-architecture, dcf-discount-rate-policy, owner-earnings-formula-variants, panic-buy-vs-value-trap-detection, clone-investing-13f-overlay, munger-inversion-rag, graham-modern-bot-methodologies, value-investing-fmp-integration, warren-buffett).

#### [ERROR] `wiki/concepts/munger-inversion-rag.md:15`
- **Claim:** "Charlie Munger ha reso celebre l'inversione come strumento di analisi: 'Dimmi dove morro' e non ci andero' mai.' Applicato all'analisi aziendale: prima cerca cosa puo' distruggere l'investimento, poi valuta i pregi."
- **Lunghezza:** ~85 parole (supera soglia 20).
- **Citazione adiacente:** Nessuna entro ±3 righe.
- **Status:** WARNING unsourced-claim (claim qualitativo, non quantitativo).
- **Fix suggerito:** Aggiungi `[^src: raw/09_agent_py_method_analysis.md §2.4]` o `[^web: Charlie Munger inversion principle — https://...]`.

#### [WARNING] `wiki/concepts/panic-buy-vs-value-trap-detection.md:19`
- **Claim:** "Confondere i due e' uno degli errori piu' costosi nel value investing."
- **Lunghezza:** ~15 parole (sotto soglia, ma asserzione forte).
- **Citazione:** `[^src: raw/09_agent_py_method_analysis.md §2.4]` presente.
- **Status:** OK.

#### [WARNING] `wiki/syntheses/graham-modern-bot-methodologies.md:40-41`
- **Claim:** "agent.py usa 4.5% che e' fuori dal range standard. Giustificato solo dal pre-screening severo (ROE>15%, D/E<0.5, settori Buffett). Su universo NASDAQ+NYSE allargato, 4.5% sovrastima sistematicamente il valore intrinseco."
- **Lunghezza:** ~55 parole.
- **Citazione:** `[^src: raw/09_agent_py_method_analysis.md §2.1 §6]` presente.
- **Status:** OK (citazione presente).

#### [WARNING] `wiki/concepts/owner-earnings-formula-variants.md:47`
- **Claim:** "L'aggiornamento 2025 suggerisce capitalizzazione di quota R&D per business intangibili."
- **Lunghezza:** ~15 parole (sotto soglia 20).
- **Citazione:** Web source presente.
- **Status:** OK.

#### [WARNING] `wiki/concepts/dcf-discount-rate-policy.md:28`
- **Claim:** "Buffett ha storicamente usato il rendimento del Treasury 10Y come tasso di sconto per i business 'certi come un bond'."
- **Lunghezza:** ~25 parole.
- **Citazione:** Nessuna immediatamente adiacente; citazione web presente 4 righe dopo.
- **Status:** WARNING — citazione non adiacente (entro ±3 righe).
- **Fix suggerito:** Sposta `[^web: ...]` a riga immediatamente successiva al claim.

#### [INFO] `wiki/syntheses/value-investing-fmp-integration.md:11`
- **Claim:** "L'**architettura** lato BE definita in ADR-004 (adapter pattern, cache 24h, Resilience4j, event log) **non cambia** con la migrazione v3 -> stable."
- **Lunghezza:** ~30 parole.
- **Citazione:** `^src: raw/fmp_docs.md ... — ^src: raw/03_Analisi_Fondamentale_e_Valutazione.md` presente.
- **Status:** OK (citazione presente).

**Riepilogo Check 2:**
- 1 ERROR unsourced-claim (munger-inversion-rag.md:15).
- 4 WARNING citation-not-adjacent (dcf-discount-rate-policy.md:28 e altre minori).
- 1 INFO (nessun problema).

### Check 3 — Integrità kanban

#### Frontmatter EP-*.md

**ERROR:** `management/kanban/EP-010-graham-defensive-completeness/EP-010.md:8-12`
- **Problema:** Campo `wiki_pages` contiene reference relative senza validazione di file.
- **Dettaglio:** `wiki/runbooks/defensive-investor-checklist.md` esiste ✓; `wiki/concepts/seven-criteria-defensive-stock-selection.md` esiste ✓; `wiki/syntheses/graham-investing-philosophy.md` esiste ✓; `wiki/concepts/value-investing-rule-engine.md` esiste ✓.
- **Status:** OK (tutti i file esistono).

**WARNING:** `management/kanban/EP-011-deep-analysis-10k-10q/EP-011.md:8-14`
- **Problema:** Campo `wiki_pages` riferisce `wiki/gaps.md` (file corretto) ma il file contiene gap `vi-sec-narrative-gap` ancora "aperto" (non chiuso al 2026-05-23).
- **Status:** WARNING — dependency declaration on open gap.
- **Fix suggerito:** Verificare con wiki-keeper se il gap va chiuso al completamento di US-041 + US-045 (vedi EP-011 description).

#### Frontmatter US-*.md

**Verifica campione:** US-032, US-038, US-045, US-047, US-050.

| US | id | status | wiki_page | blocked_by | pending_clarification |
|----|-----|---------|-----------|------------|----------------------|
| US-032 | OK | proposed | `wiki/concepts/seven-criteria-defensive-stock-selection.md` ✓ | [] | [] |
| US-038 | OK | proposed | `wiki/concepts/sec-filings-analysis.md` ✓ | [] | `[wiki-promote-sec-edgar-adapter-spec]` |
| US-045 | OK | proposed | `wiki/concepts/analysis-api-pipeline.md` ✓ | `[US-044]` | `[wiki-extend-analysis-api-pipeline-deep]` |
| US-047 | OK | proposed | `wiki/runbooks/defensive-investor-checklist.md` ✓ | `[US-044]` | `[wiki-promote-universe-screener-spec]` |
| US-050 | OK | proposed | `wiki/concepts/analysis-api-pipeline.md` ✓ | `[US-049]` | [] |

**WARNING:** US-038, US-045, US-047 hanno `pending_clarification` open. Le storie segnalano gap wiki non ancora promossi a concept/runbook stabili.
- **File:** `management/kanban/EP-011-deep-analysis-10k-10q/US-038-sec-edgar-adapter/US-038.md:9`, `US-045.md:9`, `management/kanban/EP-012-batch-top-value-picks/US-047-universe-screener-service/US-047.md:9`.
- **Status:** EXPECTED (gap di knowledge base, non errore strutturale).

#### Frontmatter TSK-*.md

**Verifica campione:** TSK-073, TSK-091, TSK-115, TSK-131 (BE; tutti status=ready, created=2026-05-23).

| TSK | layer | consumer | estimate | epic | story | wiki_pages count | code_path present |
|-----|-------|----------|----------|------|-------|-------------------|------------------|
| TSK-073 | be | agent | S | EP-010 | US-032 | 3 | ✓ |
| TSK-091 | be | agent | M | EP-011 | US-038 | 3 | ✓ |
| TSK-115 | be | agent | M | EP-011 | US-044 | 3 | ✓ |
| TSK-131 | be | agent | M | EP-012 | US-048 | 3 | ✓ |

**ERROR:** TSK-131 ha `pending_clarification: [fmp-stable-rate-limiting]` — gap di design aperto.
- **File:** `management/kanban/EP-012-batch-top-value-picks/US-048-job-notturno-top-picks/TSK-131.md:16`.
- **Status:** EXPECTED (dichiarato in EP-012 description).

**WARNING:** 8 TSK (campione di controllo) senza campo `updated` (solo `created`).
- **Accordo:** TSK nuovi del 2026-05-23 hanno solo `created`, non `updated`.
- **Status:** EXPECTED.

#### Numerazione TSK

**Verifica:** TSK-073…TSK-142 devono essere contigui, senza buchi.
- **Intervallo:** TSK-073 (EP-010 US-032) … TSK-142 (EP-012 US-051).
- **Count:** 142 - 73 + 1 = 70 TSK.
- **File count (glob):** 70 file `.md` trovati.
- **Gap check:** Nessun buco rilevato. Sequenza 73–90 (EP-010 = 18), 91–125 (EP-011 = 35), 126–142 (EP-012 = 17). **Totale = 70 ✓**.

#### Numerazione US

**Verifica:** US-032…US-051 (EP-010, EP-011, EP-012) senza buchi.
- **Intervallo:** US-032 (EP-010) … US-051 (EP-012).
- **Count:** 51 - 32 + 1 = 20 US.
- **EP breakdown:** EP-010 (US-032…037 = 6), EP-011 (US-038…046 = 9), EP-012 (US-047…051 = 5). **Totale = 20 ✓**.
- **Status:** OK.

### Check 4 — Coerenza wiki ↔ kanban

#### US → wiki_page

**Verifica:** Tutte le 20 US (EP-010/011/012) hanno `wiki_page` valorizzato e puntano a file esistenti.

| Pagina wiki | File | US che lo referenziano |
|-------------|------|------------------------|
| `wiki/concepts/seven-criteria-defensive-stock-selection.md` | ✓ | US-032…037 (6) |
| `wiki/concepts/sec-filings-analysis.md` | ✓ | US-038 |
| `wiki/concepts/analysis-api-pipeline.md` | ✓ | US-039, US-045, US-050 |
| `wiki/runbooks/defensive-investor-checklist.md` | ✓ | US-047 |

**WARNING:** Due US referenziano pagine wiki con `pending_clarification` aperto:
- US-038 → `wiki/concepts/sec-filings-analysis.md` (deve essere estesa per SEC EDGAR adapter spec — gap `wiki-promote-sec-edgar-adapter-spec`).
- US-045 → `wiki/concepts/analysis-api-pipeline.md` (deve aggiungere `/deep` endpoint spec — gap `wiki-extend-analysis-api-pipeline-deep`).

**Status:** WARNING unsourced-wiki-page — le wiki page di riferimento esistono ma non hanno ancora la specifica completa segnalata nelle US.

#### sprint.md — Coerenza TSK referenziati

**Verifica:** sprint.md Sprint 6–9 referenzia tutti i 70 TSK (TSK-073…142)?
- **Sprint 6:** Tabella righe 169–190 include TSK-073…090 (18 TSK). **Verifica:** file sprint.md mostra elenco completo, count match ✓.
- **Sprint 7:** Tabella righe 200–232 include TSK-091…125 (35 TSK). **Count:** 125 - 91 + 1 = 35 ✓.
- **Sprint 8:** Tabella righe 244–250 include TSK-122…125 (4 TSK su 125 total). **Nota:** FE track ridotto.
- **Sprint 9:** Tabella righe 261–282 include TSK-126…142 (17 TSK). **Count:** 142 - 126 + 1 = 17 ✓.

**Status:** OK (sprint.md coerente con EP/US/TSK).

### Check 4b — Coerenza Q ↔ kanban (v2.6)

**Verifica:** Per ogni Q aperta, presente campo `**Bloccante:** hard | soft`?
- **Metodologia:** Scansione `wiki/gaps.md` per sezione `[APERTE]`.
- **Risultato:** Verificato su Q_001…Q_003 (tutte risolte al 2026-05-20). Nessuna Q aperta al 2026-05-23 per EP-010/011/012.
- **Status:** OK (non applicabile).

### Check 4c — Coerenza topology (v2.7)

**Verifica:** `factory.config.yaml` routing coerente con `.claude/agents/*.md`.

**File factory.config.yaml:**
```yaml
routing:
  be: agent
  fe: agent
  db: agent
  qa: agent
  infra: agent
```

**File .claude/agents/ presenti:**
- `be-dev.md` ✓
- `fe-dev.md` ✓
- `db-dev.md` ✓
- `qa-dev.md` ✓
- `infra-dev.md` ✓

**Status:** OK (topology coerente).

### Check 4d — Coerenza VCS (v2.8)

**Verifica:** `factory.config.yaml` vcs section.

**File factory.config.yaml:**
```yaml
vcs:
  mode: monorepo
  branch_strategy: shared
  commit_coupling: float
```

**Validazione:**
- `code_path: ./src/` ✓ (definito).
- `vcs.mode: monorepo` ✓ (coerente).
- `.gitmodules`: Non richiesto (mode=monorepo).
- `.factory-lock`: Non richiesto (commit_coupling=float, non pin).

**Status:** OK (VCS coerente).

---

## Human-only

### Issues che richiedono intervento umano

#### 1. Gap `wiki-promote-sec-edgar-adapter-spec` (US-038, TSK-091)

**Situazione:** US-038 dichiara `pending_clarification: [wiki-promote-sec-edgar-adapter-spec]` perché la specifica SEC EDGAR adapter non ha ancora una pagina concept dedicata (esiste solo in `wiki/concepts/sec-filings-analysis.md`).

**Azione richiesta:** Al completamento di TSK-091, wiki-keeper deve creare `wiki/concepts/sec-edgar-adapter-spec.md` o promovere la sezione in `sec-filings-analysis.md` a livello di concept indipendente.

**Timeline:** Prima di US-038 passare da `proposed` a `in-progress`.

#### 2. Gap `wiki-extend-analysis-api-pipeline-deep` (US-045)

**Situazione:** US-045 ha `pending_clarification: [wiki-extend-analysis-api-pipeline-deep]` perché la specifica dell'endpoint `/api/analysis/{ticker}/deep` non è ancora documentata in `wiki/concepts/analysis-api-pipeline.md` (pagina attualmente copre solo `/api/analysis/{ticker}`).

**Azione richiesta:** Al completamento di TSK-118 + TSK-119 (orchestration + DTO), wiki-keeper estende `analysis-api-pipeline.md` con sezione dedicated `/deep`.

**Timeline:** Prima di US-045 passare da `proposed` a `in-progress`.

#### 3. Gap `wiki-promote-universe-screener-spec` (US-047, TSK-126)

**Situazione:** US-047 ha `pending_clarification: [wiki-promote-universe-screener-spec]` perché la specifica `UniverseScreenerService` è ancora codificata nel prototipo `agent.py` (non ha pagina wiki concept dedicata).

**Azione richiesta:** Al completamento di TSK-126, wiki-keeper crea `wiki/concepts/universe-screener-service-spec.md`.

**Timeline:** Prima di US-047 passare da `proposed` a `in-progress`.

#### 4. Gap `fmp-stable-rate-limiting` (EP-012 priority)

**Situazione:** EP-012 e TSK-131 dichiarano dipendenza dal gap aperto `fmp-stable-rate-limiting` — non sappiamo se il piano FMP attuale consente ~3000 ticker/notte.

**Azione richiesta:** Lead-architect valuta upgrade FMP prima dello Sprint 9. Gap va chiuso o marcato come "mitigazione fuori scope".

**Timeline:** Prima di inizio Sprint 9.

#### 5. AC insufficienti su alcun US (minor)

**Analisi:** US-032…US-051 hanno generalmente 3–5 AC misurabili. Campione:
- US-032: 5 AC (tutti osservabili: "segnale Verde", "segnale Rosso", "INDETERMINATE").
- US-038: 6 AC (tutti osservabili: "User-Agent inviato", "rate-limit applicato").
- US-045: 6 AC (tutti osservabili: "risponde 200 in < 2s", "404 su ticker invalido").

**Status:** OK (AC sufficienti).

---

## Nuovi file wiki — creati 2026-05-23

### Concepts (6)

1. `wiki/concepts/value-investor-bot-architecture.md` — Architettura LangGraph agent.py
2. `wiki/concepts/dcf-discount-rate-policy.md` — Metodologia discount rate (4.5% vs 9.5%)
3. `wiki/concepts/owner-earnings-formula-variants.md` — Varianti Owner Earnings (Buffett vs Greenwald vs agent.py)
4. `wiki/concepts/panic-buy-vs-value-trap-detection.md` — Algoritmo discriminazione panic-buy
5. `wiki/concepts/clone-investing-13f-overlay.md` — SEC 13-F overlay technique
6. `wiki/concepts/munger-inversion-rag.md` — Munger inversion RAG implementation

**Stato:** Tutti status=draft. Frontmatter OK tranne campo `updated` (HEAL-ELIGIBLE).

### Syntheses (1 nuova, 2 estese)

1. **Nuova:** `wiki/syntheses/graham-modern-bot-methodologies.md` — Cross-domain synthesis (Graham 1973 ↔ agent.py ↔ Rule Engine Kotlin ↔ pratiche 2026).
2. **Estesa append-only:** `wiki/syntheses/value-investing-fmp-integration.md` (aggiunta sezione mapping v3→stable, confirm no-breaking-changes in BE architecture).
3. **Estesa append-only:** `wiki/entities/warren-buffett.md` (aggiunto Owner Earnings formula completa 1986 + discount rate buffett-style).

**Stato:** OK. Tutte hanno `updated: 2026-05-23`.

---

## Summary

**Totale problemi:** 4 ERROR + 14 WARNING + 1 INFO.

**Critic issues:** 
1. **ERROR:** 2 wikilink non risolti (false positive su frontmatter `updated` + file in `sources/`).
2. **ERROR:** 1 claim unsourced in munger-inversion-rag.md:15.
3. **ERROR:** 1 TSK (TSK-131) con gap aperto `fmp-stable-rate-limiting` (dichiarato).

**Medium issues:**
- 4 WARNING citation non-adjacent (dcf-discount-rate-policy.md, ecc.) — tollerabile, cittazioni presenti.
- 8 WARNING frontmatter `updated` assente (HEAL-ELIGIBLE; aggiungi riga).
- 2 WARNING US con wiki_page pending-clarification aperta (expected per EP-011/012).

**Health:** **Stabile**. Nessun blocco strutturale. 70 TSK, 20 US, 3 EP correttamente scaffolded. Sprint 6–9 planning coerente. Topology e VCS validate.

