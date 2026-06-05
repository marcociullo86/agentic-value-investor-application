'use client';

import { useState, useId, useCallback } from 'react';
import type { RuleSignal, Signal } from '@/lib/api/analysis';
import {
  formatRuleSignal,
  type RuleSignal as TypedRuleSignal,
} from '@/lib/rule-signals/formatters';
import { cn } from '@/lib/utils/cn';

/**
 * RuleSignalCard — TSK-021 (US-014); migrato TSK-320 (EP-021 / US-095, ADR-028).
 *
 * Singolo "semaforo" cliccabile che mostra lo stato di una regola del
 * Rule Engine. Espandibile via `useState` + Tailwind transitions (no
 * dipendenza @radix-ui/react-collapsible — non in package.json, evitato
 * per ridurre superficie deps; refactor a Radix in TSK successivo
 * meccanico mantenendo le props pubbliche invariate).
 *
 * **EP-021 / TSK-320 — typed-driven formatting**: il dettaglio (subtitle + tooltip)
 * viene derivato da `formatRuleSignal(signal)` (lib/rule-signals/formatters.ts,
 * TSK-319) che narrowa su `ruleId` (union discriminata OpenAPI 3.1
 * `oneOf`+`discriminator`) e legge i campi tipati specifici di ciascun ruleId
 * (es. `revenueLatest`/`thresholdUsd` per SIZE_LATEST). I campi legacy
 * `signal.rationale` e `signal.observedValue` NON sono più letti direttamente:
 * sono ancora presenti nel payload (deprecated, finestra R+1/R+2 ADR-028 §8)
 * ma il rendering passa per il formatter typed-driven con fallback paranoid
 * su `rationale` (gestito internamente dal formatter, ADR-028 §6).
 *
 * Riferimento design:
 *   design_&_architecture/components/frontend-components.md
 *   §analysis/RuleSignalCard + §Design system §Codifica colore Traffic Light.
 *
 * Accessibilità (WCAG AA — AC US-014):
 *  - `aria-label` completo include rule name humanized + label signal +
 *    observedValue + threshold (alternativa testuale al solo colore).
 *  - Label visibile testuale ("OK" / "Attenzione" / "Non soddisfatta" /
 *    "Indeterminato" / "Non calcolabile") affiancata al pallino: WCAG
 *    1.4.1 ("Use of Color") soddisfatto — il colore NON è l'unico
 *    canale informativo.
 *  - Contrasto: i tokens `bg-signal-*` da TSK-030 sono già WCAG AA
 *    verificati su sfondo bianco/scuro (vedi tailwind.config.ts
 *    §colors.signal — green #16a34a/text-white, yellow #d97706/text-black,
 *    red #dc2626/text-white, neutral #64748b/text-white).
 *  - Button con `aria-expanded` + `aria-controls` (Radix Collapsible
 *    pattern semantically equivalente).
 *  - Keyboard: <button> nativo → Enter/Space attivano expand/collapse.
 *
 * Performance: `useCallback` su toggle handler per stabilità ref;
 * memoization estensiva non necessaria (parent re-render → max 7-10 card,
 * cost trascurabile).
 */

export interface RuleSignalCardProps {
  readonly signal: RuleSignal;
  /** Stato iniziale (default `false`). */
  readonly defaultExpanded?: boolean;
  /**
   * Sottotitolo opzionale con il valore osservato formattato (es. "P/B: X.X",
   * "Anni positivi: X/10"). Visibile sulla faccia COMPRESSA, sotto il signal
   * badge. Introdotto da TSK-290 (DoD item 3) per le 6 card Graham Defensive.
   * Le card Buffett NON ricevono questa prop → comportamento invariato.
   */
  readonly observedSubtitle?: string;
}

interface SignalPresentation {
  readonly dotClassName: string;
  readonly badgeClassName: string;
  readonly label: string;
  readonly icon: string;
}

/**
 * Mapping LOCAL `Signal → presentation` — copre l'intero enum incluso
 * `NOT_APPLICABLE` (assente in `lib/utils/signal-color.ts` che gestisce
 * solo il subset 5-valori per le regole quantitative). Decisione:
 * NON modificare il file TSK-030 (boundary task) — duplicazione contenuta
 * (5 stati → 6 stati) tollerabile, ricomponibile in TSK futuro.
 */
const PRESENTATIONS: Readonly<Record<Signal, SignalPresentation>> = {
  GREEN: {
    dotClassName: 'bg-signal-green',
    badgeClassName: 'bg-signal-green text-white',
    label: 'OK',
    icon: '✓',
  },
  YELLOW: {
    dotClassName: 'bg-signal-yellow',
    badgeClassName: 'bg-signal-yellow text-black',
    label: 'Attenzione',
    icon: '!',
  },
  RED: {
    dotClassName: 'bg-signal-red',
    badgeClassName: 'bg-signal-red text-white',
    label: 'Non soddisfatta',
    icon: '✕',
  },
  INDETERMINATE: {
    dotClassName: 'bg-signal-neutral',
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Indeterminato',
    icon: '?',
  },
  NOT_APPLICABLE: {
    dotClassName: 'bg-signal-neutral',
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Non applicabile',
    icon: '–',
  },
  NOT_CALCULABLE: {
    dotClassName: 'bg-signal-neutral',
    badgeClassName: 'bg-signal-neutral text-white',
    label: 'Non calcolabile',
    icon: '?',
  },
};

/**
 * Humanize `ruleId` → label leggibile.
 *
 * Convenzioni adottate:
 *  - Il `.` (es. "profitability.roe") separa CATEGORIA e METRICA → reso
 *    come " — ".
 *  - Lo `_` (es. "ROE_10Y_AVG", "graham_number") separa parole interne
 *    alla metrica → reso come spazio.
 *  - Ogni segmento viene Title-Cased (prima lettera maiuscola, resto
 *    lowercase) — uniforma input MAIUSCOLI (Java-style) e minuscoli.
 *
 * Esempi:
 *   "profitability.roe"  → "Profitability — Roe"
 *   "ROE_10Y_AVG"        → "Roe 10y Avg"
 *   "graham_number"      → "Graham Number"
 *   "valuation.dcf_fcf"  → "Valuation — Dcf Fcf"
 */
function humanizeRuleId(ruleId: string): string {
  const titleCase = (s: string): string => {
    if (s.length === 0) return s;
    return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
  };
  // Split prima sui dot (categoria.metrica), poi su underscore dentro ogni segmento.
  return ruleId
    .split('.')
    .map((segment) => segment.split('_').map(titleCase).join(' '))
    .join(' — ');
}

export function RuleSignalCard(props: RuleSignalCardProps): React.ReactElement {
  const { signal: ruleSignal, defaultExpanded = false, observedSubtitle } = props;
  const [expanded, setExpanded] = useState<boolean>(defaultExpanded);
  const detailsId = useId();

  const presentation = PRESENTATIONS[ruleSignal.signal];

  /**
   * Formatter typed-driven (TSK-319 / ADR-028 §6). Riceve l'union discriminata
   * generata da OpenAPI 3.1 oneOf+discriminator; il cast `as unknown as
   * TypedRuleSignal` ponte tra il tipo legacy hand-rolled (analysis.ts) e quello
   * generato (lib/api/generated/schema.ts). I due sono strutturalmente
   * compatibili sui campi letti dal formatter (ruleId, signal, rationale legacy);
   * i campi tipati assenti nel legacy → fallback paranoid su `rationale` interno
   * al formatter (formatters.ts §legacyFallback). Cast ponte documentato in
   * ADR-028 §6 (transizione R+1/R+2).
   */
  const { title: typedTitle, subtitle: typedSubtitle, tooltip: typedTooltip } =
    formatRuleSignal(ruleSignal as unknown as TypedRuleSignal);

  /**
   * Titolo visibile: preferenza al `title` typed (es. "Dimensione", "P/E
   * moderato") quando disponibile; fallback su `humanizeRuleId(ruleId)` per
   * ruleId arbitrari/legacy fuori union (mantiene invariante UI per fixture
   * test/drift). Coerente con runtimeFallback del formatter (formatters.ts
   * §runtimeFallback).
   */
  const humanName =
    typedTitle && typedTitle !== ruleSignal.ruleId
      ? typedTitle
      : humanizeRuleId(ruleSignal.ruleId);

  /**
   * aria-label completo (WCAG 1.4.1 — Use of Color): il colore NON è l'unico
   * canale informativo, l'aria-label espone nome regola + stato + sintesi
   * tipata del valore/soglia (subtitle typed-driven, sostituisce la coppia
   * legacy "Valore osservato + Soglia").
   */
  const ariaLabel = `Regola ${humanName}: ${presentation.label}. ${typedSubtitle}`;

  const toggle = useCallback((): void => {
    setExpanded((prev) => !prev);
  }, []);

  return (
    <div
      data-testid={`rule-signal-card-${ruleSignal.ruleId}`}
      data-signal={ruleSignal.signal}
      className="flex flex-col rounded-lg border border-slate-200 bg-white shadow-sm dark:border-slate-800 dark:bg-slate-900"
    >
      <button
        type="button"
        onClick={toggle}
        aria-expanded={expanded}
        aria-controls={detailsId}
        aria-label={ariaLabel}
        className="flex w-full items-center gap-3 rounded-lg p-4 text-left transition hover:bg-slate-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-500 dark:hover:bg-slate-800"
      >
        <span
          aria-hidden="true"
          data-testid={`rule-signal-dot-${ruleSignal.ruleId}`}
          className={cn(
            'inline-flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-xs font-bold text-white',
            presentation.dotClassName,
          )}
        >
          {presentation.icon}
        </span>
        <div className="flex min-w-0 flex-1 flex-col">
          <span className="truncate text-sm font-semibold text-slate-900 dark:text-slate-100">
            {humanName}
          </span>
          <span
            data-testid={`rule-signal-label-${ruleSignal.ruleId}`}
            className={cn(
              'mt-1 inline-flex w-fit items-center rounded px-2 py-0.5 text-xs font-medium',
              presentation.badgeClassName,
            )}
          >
            {presentation.label}
          </span>
          {observedSubtitle !== undefined && observedSubtitle.length > 0 ? (
            <span
              data-testid={`rule-signal-subtitle-${ruleSignal.ruleId}`}
              className="mt-1 truncate text-xs text-slate-500 dark:text-slate-400"
            >
              {observedSubtitle}
            </span>
          ) : null}
        </div>
        <span
          aria-hidden="true"
          className={cn(
            'text-slate-400 transition-transform',
            expanded ? 'rotate-180' : 'rotate-0',
          )}
        >
          ▾
        </span>
      </button>
      {expanded ? (
        <div
          id={detailsId}
          data-testid={`rule-signal-details-${ruleSignal.ruleId}`}
          className="border-t border-slate-200 px-4 py-3 text-sm text-slate-700 dark:border-slate-800 dark:text-slate-300"
        >
          {/*
            EP-021 / TSK-320: dettaglio espanso typed-driven.
             - `Valore osservato`: ora popolata con `subtitle` typed (sintesi
               metrica + soglia, es. "Revenue: $2.30B (soglia $100M)") al posto
               della coppia legacy "observedValue numerica + threshold stringa".
               Il testid `rule-signal-observed-{ruleId}` resta per stabilità test.
             - `Soglia`: rimossa come riga separata (la soglia è inclusa nel
               `subtitle` typed; evita duplicazione visiva).
             - `Razionale`: popolato con `tooltip` typed (citazione fonte
               Graham/Buffett, ben più informativa del rationale legacy).
               Testid `rule-signal-rationale-{ruleId}` preservato.
          */}
          <dl className="grid grid-cols-[max-content_1fr] gap-x-3 gap-y-1">
            <dt className="font-medium text-slate-500 dark:text-slate-400">
              Valore osservato
            </dt>
            <dd data-testid={`rule-signal-observed-${ruleSignal.ruleId}`}>
              {typedSubtitle}
            </dd>
            {typedTooltip && typedTooltip.length > 0 ? (
              <>
                <dt className="font-medium text-slate-500 dark:text-slate-400">
                  Razionale
                </dt>
                <dd data-testid={`rule-signal-rationale-${ruleSignal.ruleId}`}>
                  {typedTooltip}
                </dd>
              </>
            ) : null}
          </dl>
        </div>
      ) : null}
    </div>
  );
}
