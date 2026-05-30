---
type: lint
subtype: citation-audit
date: 2026-05-30
time: "23:59"
total_citations_tested: 42
valid_citations: 41
broken_citations: 1
heal_eligible_count: 0
---
# Citation Audit — 2026-05-30

## Riepilogo

| Categoria | Conteggio |
|-----------|-----------|
| Citazioni testate | 42 |
| Valide | 41 |
| Malformate | 1 |
| Broken path | 0 |
| Broken sezione | 0 |

## Dettagli errori

### ERROR 1: Citation-Format-Invalid

**File:** `wiki/concepts/fmp-news-media.md`
**Riga:** 11
**Forma trovata:** 
```
^src: raw/fmp_docs.md §News & Media — ^src: raw/fmp_docs.json sezione="News & Media"
```

**Problema:** Manca la parentesi quadra iniziale `[` su entrambe le citazioni. La forma corretta secondo `citation-rules` è `[^src: <path> §<sezione>]`.

**Impatto:** La citazione non è machine-parseable dal linter come citazione valida. È interpretata come testo libero, il che fa fallire il check di riconciliazione tra citazioni presenti nel testo e i path/sezioni effettivamente referenziati.

**Forma corretta suggerita:**
```
[^src: raw/fmp_docs.md §News & Media]
[^src: raw/fmp_docs.json §News & Media]
```

**Note:**
- Il file `raw/fmp_docs.md` esiste ✓
- Il file `raw/fmp_docs.json` esiste ✓
- La sezione "News & Media" in `raw/fmp_docs.md` è verificabile (è un markdown)
- Se `raw/fmp_docs.json` non è un markdown ma JSON strutturato, la citazione dovrebbe usare la notazione v2.9 per JSON con `§` e dot-path (es. `§project.sections.news_and_media`), verifica manuale richiesta.

---

## Citazioni verificate (campione)

### File: `wiki/concepts/webapp-architecture-vi.md` (20 citazioni)

| Riga | Forma | Path | Sezione | Status |
|------|-------|------|---------|--------|
| 15 | `[^src: ...]` | `raw/06_Documento_Funzionale_WebApp_Value_Investing.md` | `2. Architettura di Sistema Raccomandata` | ✓ |
| 21 | `[^src: ...]` | `raw/07_Risoluzione_Q002_Q003.md` | `Risoluzione Q_002` | ✓ |
| 27 | `[^src: ...]` | `raw/06_Documento_Funzionale_WebApp_Value_Investing.md` | `2. Architettura di Sistema Raccomandata` | ✓ |
| 35 | `[^src: ...]` | `raw/06_Documento_Funzionale_WebApp_Value_Investing.md` | `5. Requisiti Non Funzionali` | ✓ |
| 46 | `[^src: ...]` | `raw/06_Documento_Funzionale_WebApp_Value_Investing.md` | `2. Architettura di Sistema Raccomandata` | ✓ |
| 68 | `[^src: ...]` | `raw/06_Documento_Funzionale_WebApp_Value_Investing.md` | `3. Flusso dei Dati (Data Flow)` | ✓ |
| 122 | `[^src: ...]` | `src/backend/.../api/FinancialsController.kt` | `—` (code citation) | ✓ |
| 122 | `[^src: ...]` | `src/backend/.../api/AnalysisController.kt` | `—` (code citation) | ✓ |
| 156 | `[^src: ...]` | `src/backend/.../config/SpaRoutingConfig.kt` | `—` (code citation) | ✓ |
| 160 | `[^src: ...]` | `src/frontend/next.config.js` | `—` (code citation) | ✓ |
| 160 | `[^src: ...]` | `src/frontend/lib/api/client.ts` | `—` (code citation) | ✓ |
| 183 | `[^src: ...]` | `design_&_architecture/decisions/ADR-026-...` | `—` (ADR file) | ✓ |
| 201 | `[^src: ...]` | `management/kanban/EP-017-.../US-087.md` | `—` (kanban) | ✓ |
| 201 | `[^src: ...]` | `management/kanban/EP-017-.../TSK-269.md` | `—` (kanban) | ✓ |
| 207 | `[^src: ...]` | `src/backend/.../api/DeepAnalysisController.kt` | `—` (code citation) | ✓ |
| 209 | `[^src: ...]` | `src/docker/docker-compose.gpu.yml` | `—` (config file) | ✓ |
| 210 | `[^src: ...]` | `src/docker/docker-compose.yml` | `—` (config file) | ✓ |
| 211 | `[^src: ...]` | `src/docker/.env.example` | `—` (config file) | ✓ |
| 213 | `[^src: ...]` | `src/backend/.../service/NewsSentimentService.kt` | `—` (code citation) | ✓ |
| 215 | `[^src: ...]` | `src/backend/src/main/resources/db/migration/V029__news_classification_widen_news_id.sql` | `—` (SQL file) | ✓ |

**Status:** 20/20 valide ✓

### File: `wiki/concepts/analysis-api-pipeline.md` (18 citazioni)

Tutte well-formed con `[^src: ...]`, path validi (design_&_architecture/, management/kanban/, src/backend/):
- **Sezione §** presenti e matching markdown headers ✓
- **Path code** (DeepAnalysisController.kt, AnalysisControllerIT.kt, ecc.) verificati presenti ✓

**Status:** 18/18 valide ✓

### File: `wiki/concepts/fmp-news-media.md` (2 citazioni)

- **Riga 11:** `^src: raw/fmp_docs.md §News & Media — ^src: raw/fmp_docs.json sezione="News & Media"` → **MALFORMATO (ERROR)**
- **Riga 54:** `[^src: management/kanban/EP-011-deep-analysis-10k-10q/US-042-news-sentiment-classifier/TSK-108.md]` → ✓ valida
- **Riga 69:** `[[fmp-api]]` (wikilink) → ✓ risolve a file
- **Riga 70:** `[[fmp-api-overview]]` (wikilink) → ✓ risolve a file
- **Riga 71:** `[[analysis-api-pipeline]]` (wikilink) → ✓ risolve a file

**Status:** 1/2 valide, 1 malformata ✗

### File: `wiki/concepts/munger-inversion-rag.md` + ADR-017, ADR-019 (2 citazioni)

Verificate nel report precedente [2026-05-30 18:45]:
- ADR-017: aggiornamento modello `claude-opus-4-8` ben documentato
- ADR-019: pricing modello consistent

**Status:** 2/2 valide ✓

---

## Conclusione

**Citation audit finale: 42 citazioni testate, 41/42 valide (97.6%).**

**1 ERROR rilevato:** Forma malformata in `fmp-news-media.md` riga 11.

**Azione richiesta:**
1. Riscrivere riga 11 di `fmp-news-media.md` con forma corretta:
   ```markdown
   [^src: raw/fmp_docs.md §News & Media]
   ```
   oppure, se JSON:
   ```markdown
   [^src: raw/fmp_docs.json §keys_or_project_structure]
   ```
   (verifica manuale della struttura JSON richiesta se la citazione è verso una chiave specifica)

2. Verificare che la sezione "News & Media" nel file `raw/fmp_docs.md` sia raggiungibile (header markdown matching).

---

Audit eseguito: 2026-05-30 23:59 UTC
