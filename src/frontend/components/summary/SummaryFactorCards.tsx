'use client';

import Link from 'next/link';
import { ArrowRight, BookOpen } from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  CardTitle,
} from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { cn } from '@/lib/utils/cn';
import {
  analysisBaseUrl,
  deepAnalysisUrl,
  technicalAnalysisUrl,
} from '@/lib/utils/analysis-url';
import type {
  ViVerdict,
  DeepVerdict,
  DeepAnalysisStatus,
  SummaryRationale,
} from '@/lib/api/summary';
import type { EntryTimingVerdict } from '@/lib/api/technical';

/**
 * SummaryFactorCards — TSK-343 (US-104, EP-024 Fase 2).
 *
 * Riga di 3 card "Fattori chiave" del tab Riepilogo:
 *
 *   [ Verdetto VI        ] [ Deep Analysis           ] [ Technical Analysis ]
 *    viSummary + ViChip    deepSummary + DeepChip       taSummary + TaChip
 *    → Vedi Analisi Base   → Vedi Deep                  → Vedi Technical
 *                          (CTA "Indicizza filing →"
 *                           se status = NOT_INDEXED)
 *
 * Sorgenti contratto + spec:
 *  - OpenAPI §schemas/SummaryVerdictResponse (US-103) — vi/deep/taSummary,
 *    viVerdict, deepVerdict, deepAnalysisStatus, taVerdict.
 *  - US-104 §"Layout" 3 (3 card fattori chiave) + §AC.
 *  - ADR-030 §4 (presentazione card + link cross-tab).
 *
 * UX governance — chip verdetto distinte dalla palette VI ufficiale:
 *  Il VerdettoChip riusa il pattern delle palette di
 *  `MrMarketSentimentBadge`/`LongTermTrendBadge` (palette TENUE, NON le
 *  shade dei 13 ruleSignals). Le 3 card sono "fattori" — non sostituiscono
 *  il TrafficLightPanel del tab Analisi Base.
 *
 * Accessibility (WCAG 2.2 AA — EP-016):
 *  - Ogni card è un landmark `<article>` con heading interno (`<h3>`).
 *  - Chip verdetto: `role="status"` + `aria-label` esplicito.
 *  - Link "Vedi →" usa `<Link>` Next.js (focus-visible, tastiera).
 *  - CTA "Indicizza filing →" è `<Button variant="primary">` (TSK-343 §AC).
 */

/* ------------------------------------------------------------------ */
/*  Chip primitives                                                     */
/* ------------------------------------------------------------------ */

interface ChipPresentation {
  readonly className: string;
  readonly label: string;
}

const VI_CHIPS: Readonly<Record<ViVerdict, ChipPresentation>> = {
  GREEN_DOMINANT: {
    className:
      'bg-green-100 text-green-900 border-green-300 ' +
      'dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    label: 'VI: GREEN',
  },
  YELLOW_DOMINANT: {
    className:
      'bg-amber-100 text-amber-900 border-amber-300 ' +
      'dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
    label: 'VI: YELLOW',
  },
  RED_DOMINANT: {
    className:
      'bg-red-100 text-red-900 border-red-300 ' +
      'dark:bg-red-950 dark:text-red-200 dark:border-red-800',
    label: 'VI: RED',
  },
  INDETERMINATE_DOMINANT: {
    className:
      'bg-slate-100 text-slate-800 border-slate-300 ' +
      'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700',
    label: 'VI: INDETERMINATO',
  },
};

const DEEP_CHIPS: Readonly<Record<DeepVerdict, ChipPresentation>> = {
  OK: {
    className:
      'bg-green-100 text-green-900 border-green-300 ' +
      'dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    label: 'Munger: OK',
  },
  WATCHLIST: {
    className:
      'bg-amber-100 text-amber-900 border-amber-300 ' +
      'dark:bg-amber-950 dark:text-amber-100 dark:border-amber-800',
    label: 'Munger: WATCHLIST',
  },
  RISCHIO_ESTREMO: {
    className:
      'bg-red-100 text-red-900 border-red-300 ' +
      'dark:bg-red-950 dark:text-red-200 dark:border-red-800',
    label: 'Munger: RISCHIO ESTREMO',
  },
};

const TA_CHIPS: Readonly<Record<EntryTimingVerdict, ChipPresentation>> = {
  ENTRY_FAVORABLE: {
    className:
      'bg-green-100 text-green-900 border-green-300 ' +
      'dark:bg-green-950 dark:text-green-200 dark:border-green-800',
    label: 'TA: FAVOREVOLE',
  },
  ENTRY_NEUTRAL: {
    className:
      'bg-slate-100 text-slate-800 border-slate-300 ' +
      'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700',
    label: 'TA: NEUTRO',
  },
  ENTRY_UNFAVORABLE: {
    className:
      'bg-red-100 text-red-900 border-red-300 ' +
      'dark:bg-red-950 dark:text-red-200 dark:border-red-800',
    label: 'TA: SFAVOREVOLE',
  },
  WAIT: {
    className:
      'bg-blue-100 text-blue-900 border-blue-300 ' +
      'dark:bg-blue-950 dark:text-blue-200 dark:border-blue-800',
    label: 'TA: ASPETTA',
  },
  INDETERMINATE: {
    className:
      'bg-slate-100 text-slate-800 border-slate-300 ' +
      'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700',
    label: 'TA: INDETERMINATO',
  },
};

function Chip(props: ChipPresentation): React.ReactElement {
  return (
    <span
      role="status"
      aria-label={props.label}
      className={cn(
        'inline-flex w-fit items-center rounded-full border px-2.5 py-0.5 ' +
          'text-xs font-semibold',
        props.className,
      )}
    >
      {props.label}
    </span>
  );
}

/* ------------------------------------------------------------------ */
/*  Shared factor card scaffold                                         */
/* ------------------------------------------------------------------ */

interface FactorCardProps {
  readonly title: string;
  readonly chip: React.ReactNode;
  readonly summary: string;
  readonly children?: React.ReactNode;
  readonly testId: string;
}

function FactorCard(props: FactorCardProps): React.ReactElement {
  return (
    <Card
      data-testid={props.testId}
      className="flex h-full flex-col"
      role="article"
    >
      <CardHeader>
        <CardTitle as="h3">{props.title}</CardTitle>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-3">
        <div className="flex flex-wrap items-center gap-2">{props.chip}</div>
        <p className="flex-1 text-sm text-on-surface/80">{props.summary}</p>
        {props.children}
      </CardContent>
    </Card>
  );
}

/* ------------------------------------------------------------------ */
/*  Public component                                                    */
/* ------------------------------------------------------------------ */

export interface SummaryFactorCardsProps {
  readonly ticker: string;
  readonly rationale: SummaryRationale;
  readonly viVerdict: ViVerdict;
  readonly deepAnalysisStatus: DeepAnalysisStatus;
  readonly deepVerdict: DeepVerdict | null;
  readonly taVerdict: EntryTimingVerdict | null;
}

export function SummaryFactorCards(
  props: SummaryFactorCardsProps,
): React.ReactElement {
  const {
    ticker,
    rationale,
    viVerdict,
    deepAnalysisStatus,
    deepVerdict,
    taVerdict,
  } = props;

  return (
    <section
      data-testid="summary-factor-cards"
      aria-label="Fattori chiave del verdetto"
      className="grid gap-4 md:grid-cols-3"
    >
      <ViFactorCard
        ticker={ticker}
        summary={rationale.viSummary}
        viVerdict={viVerdict}
      />
      <DeepFactorCard
        ticker={ticker}
        summary={rationale.deepSummary}
        deepAnalysisStatus={deepAnalysisStatus}
        deepVerdict={deepVerdict}
      />
      <TaFactorCard
        ticker={ticker}
        summary={rationale.taSummary}
        taVerdict={taVerdict}
      />
    </section>
  );
}

/* ------------------------------------------------------------------ */
/*  Card 1 — Verdetto VI                                                */
/* ------------------------------------------------------------------ */

function ViFactorCard({
  ticker,
  summary,
  viVerdict,
}: {
  readonly ticker: string;
  readonly summary: string;
  readonly viVerdict: ViVerdict;
}): React.ReactElement {
  const chipPresentation = VI_CHIPS[viVerdict];
  return (
    <FactorCard
      title="Verdetto VI"
      chip={<Chip {...chipPresentation} />}
      summary={summary}
      testId="summary-card-vi"
    >
      <Link
        href={analysisBaseUrl(ticker)}
        data-testid="summary-card-vi-link"
        className={cn(
          'mt-auto inline-flex items-center gap-1 text-sm font-medium ' +
            'text-blue-600 hover:underline focus-visible:outline-none ' +
            'focus-visible:ring-2 focus-visible:ring-blue-500 ' +
            'dark:text-blue-400',
        )}
      >
        Vedi Analisi Base
        <ArrowRight aria-hidden="true" className="h-3.5 w-3.5" />
      </Link>
    </FactorCard>
  );
}

/* ------------------------------------------------------------------ */
/*  Card 2 — Deep Analysis (con CTA Indicizza filing per NOT_INDEXED)   */
/* ------------------------------------------------------------------ */

function DeepFactorCard({
  ticker,
  summary,
  deepAnalysisStatus,
  deepVerdict,
}: {
  readonly ticker: string;
  readonly summary: string | null;
  readonly deepAnalysisStatus: DeepAnalysisStatus;
  readonly deepVerdict: DeepVerdict | null;
}): React.ReactElement {
  // Chip: solo se deepVerdict popolato (status = AVAILABLE).
  const chipNode =
    deepVerdict !== null ? (
      <Chip {...DEEP_CHIPS[deepVerdict]} />
    ) : (
      <Chip
        className={
          'bg-slate-100 text-slate-800 border-slate-300 ' +
          'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700'
        }
        label={
          deepAnalysisStatus === 'NOT_INDEXED'
            ? 'Munger: NON INDICIZZATA'
            : 'Munger: NON DISPONIBILE'
        }
      />
    );

  // Summary fallback quando il BE ha lasciato deepSummary = null.
  const summaryText =
    summary ??
    (deepAnalysisStatus === 'NOT_INDEXED'
      ? 'La Deep Analysis (Munger inversion) non è ancora stata eseguita per ' +
        'questo ticker. Indicizza i filing SEC per attivarla — non blocca il ' +
        'verdetto del Riepilogo, ma aggiunge una dimensione qualitativa.'
      : 'La Deep Analysis non è tecnicamente disponibile per questo ticker ' +
        '(es. filing assenti o run fallita). Il verdetto del Riepilogo rimane ' +
        'valido sui pilastri VI + TA.');

  return (
    <FactorCard
      title="Deep Analysis"
      chip={chipNode}
      summary={summaryText}
      testId="summary-card-deep"
    >
      <div className="mt-auto flex flex-wrap items-center gap-3">
        {/* CTA primaria SOLO quando NOT_INDEXED — linka al tab Deep con
            l'azione "Indicizza filing" già esistente lì (EP-011). */}
        {deepAnalysisStatus === 'NOT_INDEXED' ? (
          <Button
            asChild
            variant="primary"
            size="sm"
            data-testid="summary-card-deep-ingest-cta"
          >
            <Link href={deepAnalysisUrl(ticker)}>
              Indicizza filing
              <ArrowRight aria-hidden="true" className="ml-1 h-3.5 w-3.5" />
            </Link>
          </Button>
        ) : null}
        <Link
          href={deepAnalysisUrl(ticker)}
          data-testid="summary-card-deep-link"
          className={cn(
            'inline-flex items-center gap-1 text-sm font-medium ' +
              'text-blue-600 hover:underline focus-visible:outline-none ' +
              'focus-visible:ring-2 focus-visible:ring-blue-500 ' +
              'dark:text-blue-400',
          )}
        >
          Vedi Deep
          <ArrowRight aria-hidden="true" className="h-3.5 w-3.5" />
        </Link>
      </div>
    </FactorCard>
  );
}

/* ------------------------------------------------------------------ */
/*  Card 3 — Technical Analysis                                         */
/* ------------------------------------------------------------------ */

function TaFactorCard({
  ticker,
  summary,
  taVerdict,
}: {
  readonly ticker: string;
  readonly summary: string | null;
  readonly taVerdict: EntryTimingVerdict | null;
}): React.ReactElement {
  // taVerdict può essere null quando la TA non è calcolabile (FMP indisponibile,
  // serie EOD troppo corta da bocciare interamente il computo).
  const chipNode =
    taVerdict !== null ? (
      <Chip {...TA_CHIPS[taVerdict]} />
    ) : (
      <Chip
        className={
          'bg-slate-100 text-slate-800 border-slate-300 ' +
          'dark:bg-slate-900 dark:text-slate-200 dark:border-slate-700'
        }
        label="TA: NON DISPONIBILE"
      />
    );

  const summaryText =
    summary ??
    'Il layer di timing TA non è calcolabile per questo ticker (dati di mercato ' +
      'upstream non disponibili). Il verdetto Riepilogo si appoggia ai pilastri ' +
      'VI + Deep.';

  return (
    <FactorCard
      title="Technical Analysis"
      chip={chipNode}
      summary={summaryText}
      testId="summary-card-ta"
    >
      <Link
        href={technicalAnalysisUrl(ticker)}
        data-testid="summary-card-ta-link"
        className={cn(
          'mt-auto inline-flex items-center gap-1 text-sm font-medium ' +
            'text-blue-600 hover:underline focus-visible:outline-none ' +
            'focus-visible:ring-2 focus-visible:ring-blue-500 ' +
            'dark:text-blue-400',
        )}
      >
        Vedi Technical Analysis
        <BookOpen aria-hidden="true" className="ml-0.5 h-3.5 w-3.5" />
      </Link>
    </FactorCard>
  );
}
