---
created: 2026-05-20
updated: 2026-05-28
status: resolved
---
# Questions — App Template Demo

Domande bloccanti gestite via gate L4 graduato (v2.6, PATTERN.md §7 r.9).
Formato canonico: vedi `.claude/skills/apri-question.md`.

`status:` resta `open` finché esiste almeno una Q in `[APERTE]`,
indipendentemente dal `blocking_level`. Diventa `resolved` quando `[APERTE]` è vuota.

## [APERTE]

## [RISOLTE]

### Q_005 — Dichiarazione formale scope PCI-DSS
**Origine:** [[fintech-security-compliance]]
**Tipo:** Requisito incompleto
**Impatto:** BASSO
**Bloccante:** soft
**Domanda:** REQ-05 §5.4 richiede una dichiarazione esplicita dello scope PCI-DSS: se l'applicazione tratta dati di carta di pagamento si applicano vincoli stringenti (tokenization, iframe provider certificato, flusso dati carta documentato); altrimenti, la non-applicabilità deve essere dichiarata esplicitamente in un ADR di sicurezza. L'applicazione è un tool di screening azionario value investing e non tratta pagamenti — serve un ADR formale che dichiari "PCI-DSS: non applicabile" con le motivazioni.
**Epiche bloccate:** EP-018 (parziale)
**Storie impattate (soft, non blocked):** US-082 (`pending_clarification: [Q_005]`, `status: ready`)
[^src: wiki/concepts/fintech-security-compliance.md §5.4 — Scope PCI-DSS (condizionale)]
[^src: wiki/gaps.md §fintech-pci-dss-scope]

**Risoluzione (2026-05-28):** ADR-025 — "Security Hardening, Threat Model & PCI-DSS Non-Applicability" (`status: accepted`, deciders: lead-architect + simone.olivieri, resolves: [Q_005]) — **PCI-DSS: non applicabile**. L'applicazione è un tool di screening azionario value investing: nessun flusso pagamento, nessun campo PAN/CVV/expiry nello schema DB, nessun provider Stripe/Adyen/Checkout.com; uniche integrazioni esterne FMP (dati di mercato) e Anthropic (LLM). REQ-05 §5.4 soddisfatto con dichiarazione formale in ADR-025 §8. Verifica QA (TSK-237): grep codebase senza match flussi carta.
- **resolved_date:** 2026-05-28
- **resolved_by:** ADR-025 (§8 PCI-DSS Non Applicabile)
- **Storie sbloccate:** US-082
[^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §8]

---

### Q_004 — Strategia design system React: M3 token system vs componenti attuali
**Origine:** [[material-design-3-accessibility]]
**Tipo:** Conflitto business
**Impatto:** MEDIO
**Bloccante:** soft
**Domanda:** REQ-03 prescrive l'adozione del design token system Material Design 3 (colori, tipografia, shape, componenti M3). Il frontend attuale usa componenti basati su libreria diversa (Radix-based). Come conciliare? Tre opzioni: (a) adottare una libreria che implementa M3 nativamente per React, (b) estendere i componenti attuali con un token system M3-aligned mantenendo la libreria corrente, (c) approccio ibrido. La scelta impatta tutti i componenti UI esistenti e determina la fattibilità delle storie di EP-016 relative ai componenti M3.
**Epiche bloccate:** EP-016 (parziale — solo storie relative ai componenti M3)
**Storie bloccate:** US-069
[^src: wiki/concepts/material-design-3-accessibility.md §Design Token System M3]
[^src: wiki/syntheses/fintech-hardening-requirements-map.md §Gap identificati]
[^src: wiki/gaps.md §fintech-design-system-react]

**Risoluzione (2026-05-27):** ADR-023 — "Design Token System: shadcn/ui + Semantic Token M3-Aligned" (status: accepted 2026-05-26, deciders: lead-architect + simone.olivieri, resolves: [Q_004]) — Opzione (b): design token system shadcn/ui con semantic token M3-aligned (colori OKLCH, tipografia, shape, motion). Componenti shadcn/ui estesi con layer di design token, non sostituiti con MUI. Motivazione prioritaria: `raw/tech_stack.md` (priorità assoluta PATTERN §7 r.10) prescrive "Tailwind CSS + Radix UI primitives". EP-016 completata (10/10 TSK done, 4/4 US done). US-069 sbloccata e completata.
- **resolved_date:** 2026-05-27
- **resolved_by:** ADR-023 (Design Token System: shadcn/ui + Semantic Token M3-Aligned)
- **Storie sbloccate:** US-069
[^src: design_&_architecture/decisions/ADR-023-design-token-system-shadcn.md §Decisione]
[^src: wiki/gaps.md §fintech-design-system-react]

---

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
