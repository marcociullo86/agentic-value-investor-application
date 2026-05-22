'use client';

import { useMemo } from 'react';
import { useRouter } from 'next/navigation';
import { AgGridReact } from 'ag-grid-react';
import type {
  ColDef,
  RowClickedEvent,
  ValueFormatterParams,
} from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import { formatMarketCap } from '@/lib/utils/formatters';
import { analysisUrl } from '@/lib/utils/analysis-url';

/**
 * ResultsList — TSK-007 (US-003).
 *
 * Componente puro (props-only, no fetch interna) condiviso tra ricerca
 * (US-001) e screener (US-002) per la visualizzazione tabulare avanzata
 * dei candidati. Usa Ag-Grid Community 32.x (tema Quartz / Quartz-dark).
 *
 * Riferimento design: design_&_architecture/components/frontend-components.md
 *   §search/ResultsList.
 * Riferimento ADR: design_&_architecture/decisions/ADR-001-frontend-stack.md
 *   §Decisione (Ag-Grid Community come data grid).
 * Riferimento AC: management/kanban/EP-001-ricerca-e-screening/
 *   US-003-visualizza-risultati-ricerca/US-003.md §AC.
 *
 * Tipo unificato `ResultsListItem`: superset structurally compatibile sia
 * con `SearchResultItem` (lib/api/search.ts, TSK-003) sia con
 * `ScreenerResultItem` (lib/api/screener.ts, TSK-006). Entrambe le
 * sorgenti hanno gli stessi 4 campi `(ticker, companyName, sector?,
 * marketCapUsd?)`, quindi l'union "naturale" coincide con il superset:
 * il tipo qui definito è drop-in replace per entrambi senza adapter.
 *
 * NOTA: non importiamo né `SearchResultItem` né `ScreenerResultItem` per
 * tenere il componente disaccoppiato dai moduli API (principio di
 * inversione: il dominio del componente non dipende dal dominio API).
 */

export interface ResultsListItem {
  readonly ticker: string;
  readonly companyName: string;
  readonly sector?: string | null;
  readonly marketCapUsd?: number | null;
}

export interface ResultsListProps {
  /** Righe da renderizzare. Lista vuota → empty state (no grid). */
  readonly items: ReadonlyArray<ResultsListItem>;
  /** Se true, mostra skeleton placeholder (3-5 righe finte). */
  readonly loading?: boolean;
  /** Messaggio mostrato in empty state. Default: "Nessun risultato disponibile". */
  readonly emptyMessage?: string;
  /**
   * Se true, applica il tema Quartz dark di Ag-Grid. Default false.
   * Il parent decide leggendo il toggle dark mode globale (Tailwind
   * `darkMode: 'class'` da TSK-030); non lo deduciamo internamente per
   * mantenere il componente puro/SSR-safe.
   */
  readonly dark?: boolean;
}

const SKELETON_ROWS = 5;
const DEFAULT_EMPTY_MESSAGE = 'Nessun risultato disponibile';

/**
 * Altezza fissa h-[600px] motivata: liste tipiche dello screener possono
 * superare 50-200 righe (limit OpenAPI default 50, max 200 per page).
 * Con `domLayout="autoHeight"` la pagina diventa scrollabile globalmente
 * e il header colonne scompare oltre il fold, peggiorando UX. Con altezza
 * fissa Ag-Grid abilita lo scroll interno e tiene gli headers pinned →
 * soddisfa AC US-003 "lista > viewport ha scrollbar visibile".
 */
const GRID_CONTAINER_CLASS = 'h-[600px] w-full';

function marketCapFormatter(
  params: ValueFormatterParams<ResultsListItem, number | null | undefined>,
): string {
  const value = params.value;
  if (value === null || value === undefined || !Number.isFinite(value)) {
    return '—';
  }
  return formatMarketCap(value);
}

export function ResultsList({
  items,
  loading = false,
  emptyMessage = DEFAULT_EMPTY_MESSAGE,
  dark = false,
}: ResultsListProps): React.ReactElement {
  const router = useRouter();

  // ColDefs memoizzate: Ag-Grid re-istanzia colonne se cambia il riferimento.
  const columnDefs = useMemo<ColDef<ResultsListItem>[]>(
    () => [
      {
        field: 'ticker',
        headerName: 'Ticker',
        sortable: true,
        sort: 'asc', // ordine default alfabetico per ticker (US-003 BR)
        filter: true,
        cellClass: 'font-mono font-semibold',
        minWidth: 100,
      },
      {
        field: 'companyName',
        headerName: 'Nome',
        sortable: true,
        filter: true,
        flex: 2,
        minWidth: 180,
      },
      {
        field: 'sector',
        headerName: 'Settore',
        sortable: true,
        filter: true,
        flex: 1,
        minWidth: 140,
        valueFormatter: (p): string =>
          p.value === null || p.value === undefined || p.value === ''
            ? '—'
            : String(p.value),
      },
      {
        field: 'marketCapUsd',
        headerName: 'Market Cap',
        sortable: true,
        filter: 'agNumberColumnFilter',
        type: 'rightAligned',
        valueFormatter: marketCapFormatter,
        cellClass: 'font-mono',
        minWidth: 140,
      },
    ],
    [],
  );

  const defaultColDef = useMemo<ColDef<ResultsListItem>>(
    () => ({
      resizable: true,
      suppressMovable: false,
    }),
    [],
  );

  function handleRowClicked(event: RowClickedEvent<ResultsListItem>): void {
    const row = event.data;
    if (row === undefined) return;
    router.push(analysisUrl(row.ticker));
  }

  // Loading state — skeleton placeholder
  if (loading) {
    return (
      <div
        className="flex flex-col gap-2"
        aria-busy="true"
        aria-live="polite"
        data-testid="results-list-loading"
      >
        {Array.from({ length: SKELETON_ROWS }).map((_, idx) => (
          <div
            key={idx}
            className="h-10 animate-pulse rounded-md bg-slate-100 dark:bg-slate-800"
          />
        ))}
      </div>
    );
  }

  // Empty state — niente grid, messaggio dedicato (US-003 BR + AC)
  if (items.length === 0) {
    return (
      <p
        role="status"
        className="rounded-md border border-slate-300 bg-slate-50 p-4 text-center text-sm text-slate-600 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-300"
        data-testid="results-list-empty"
      >
        {emptyMessage}
      </p>
    );
  }

  const themeClass = dark ? 'ag-theme-quartz-dark' : 'ag-theme-quartz';

  return (
    <div
      className={`${themeClass} ${GRID_CONTAINER_CLASS}`}
      data-testid="results-list-grid"
      aria-label="Lista risultati"
    >
      <AgGridReact<ResultsListItem>
        rowData={items as ResultsListItem[]}
        columnDefs={columnDefs}
        defaultColDef={defaultColDef}
        onRowClicked={handleRowClicked}
        rowClass="cursor-pointer"
        animateRows={true}
        suppressCellFocus={false}
        // role="grid" è nativo di Ag-Grid (accessibility WCAG)
      />
    </div>
  );
}
