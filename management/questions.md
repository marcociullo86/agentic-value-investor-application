---
created: 2026-05-20
updated: 2026-05-20
status: resolved
---
# Questions — App Template Demo

Domande bloccanti gestite via gate L4 graduato (v2.6, PATTERN.md §7 r.9).
Formato canonico: vedi `.claude/skills/apri-question.md`.

`status:` resta `open` finché esiste almeno una Q in `[APERTE]`,
indipendentemente dal `blocking_level`. Diventa `resolved` quando `[APERTE]` è vuota.

## [APERTE]

<!-- Nessuna question aperta al 2026-05-20. -->

## [RISOLTE]

### Q_001 — Formula puntuale degli Owner Earnings per il DCF
**Origine:** [[value-investing-rule-engine]]
**Tipo:** Requisito incompleto
**Impatto:** ALTO
**Bloccante:** hard
**Domanda:** Quale è la formula esatta degli Owner Earnings da implementare nel motore DCF (RF4)? La FSD cita "Free Cash Flow o Owner Earnings" senza specificare composizione. La definizione canonica Buffett (Utile Netto + Ammortamenti - CapEx di mantenimento - variazione capitale circolante) richiede di esplicitare la "CapEx di mantenimento", che non è esposta direttamente da FMP.
**Epiche bloccate:** EP-004
**Storie bloccate:** US-012
[^src: wiki/concepts/value-investing-rule-engine.md §Calcolo Valore Intrinseco (RF4)]

**Risoluzione (2026-05-20):** Formula adottata = definizione canonica Buffett 1986 con stima della Maintenance CapEx tramite **modello Greenwald (primario)** e fallback su **FCF standard** quando i dati PPE/Revenue sono insufficienti. Tre metodi di stima documentati con criteri di scelta. Storia sbloccata: US-012.
[^src: wiki/sources/vi-08-risoluzione-q001-owner-earnings.md §Dettaglio]
[^src: wiki/concepts/value-investing-rule-engine.md §Aggiornamenti (v2026-05-20)]

---

### Q_002 — Scelta definitiva del framework SPA frontend
**Origine:** [[webapp-architecture-vi]]
**Tipo:** Requisito incompleto
**Impatto:** ALTO
**Bloccante:** hard
**Domanda:** Quale framework SPA va adottato per il frontend della WebApp Value Investing? La FSD lascia aperti tre candidati (React, Vue.js, Angular). La scelta condiziona la realizzazione delle storie di Dashboard (EP-005). Serve un ADR formale.
**Epiche bloccate:** EP-005
**Storie bloccate:** US-014, US-015, US-016
[^src: wiki/concepts/webapp-architecture-vi.md §Livello 1: Frontend (Client)]

**Risoluzione (2026-05-20):** ADR formalizzato → framework **React con Next.js in modalità SPA/SSG**. Motivazioni: ecosistema data-grid/charting, componentizzazione modulare delle metriche, longevità community. State management (Zustand vs Redux Toolkit) demandato al team dev. Storie sbloccate: US-014, US-015, US-016.
[^src: wiki/sources/vi-07-risoluzione-q002-q003.md §ADR Q_002: Scelta Framework Frontend]
[^src: wiki/concepts/webapp-architecture-vi.md §Aggiornamenti (v2026-05-20)]

---

### Q_003 — Criteri esatti del screener parametrico
**Origine:** [[vi-06-webapp-value-investing-fsd]]
**Tipo:** Requisito incompleto
**Impatto:** MEDIO
**Bloccante:** soft
**Domanda:** Quali sono le fasce di capitalizzazione e la lista chiusa di settori industriali esposti come filtri in RF1? La FSD cita genericamente "capitalizzazione e settore" ma non fornisce le soglie o l'enumerazione. La scelta è additiva: l'epica EP-001 può procedere su US-001 e US-003 in parallelo.
**Epiche bloccate:** EP-001 (parziale)
**Storie bloccate:** US-002
[^src: wiki/sources/vi-06-webapp-value-investing-fsd.md §RF1: Motore di Ricerca e Screening]

**Risoluzione (2026-05-20):** Criteri definiti = **fasce di capitalizzazione di mercato** (cinque fasce allineate alla progressione storica Buffett) + **lista chiusa di settori GICS** come filtro Circle of Competence. Storia rilasciata da `pending_clarification`: US-002.
[^src: wiki/sources/vi-07-risoluzione-q002-q003.md §Criteri Q_003: Screener Parametrico Buffett/Graham]
