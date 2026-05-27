'use client';

import { useEffect, useState } from 'react';
import { Card } from '@/components/ui/Card';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { toUserMessage } from '@/lib/to-user-message';
import {
  MOAT_STATUS_LABELS,
  MOAT_TYPE_DESCRIPTIONS,
  MOAT_TYPE_LABELS,
  fetchMoatChecklist,
  upsertMoatEntry,
  type MoatChecklistEntry,
  type MoatStatus,
  type MoatType,
} from '@/lib/api/moat';

/**
 * MoatChecklist component (TSK-027, US-016).
 *
 * - Renders the 4 moat categories (INTANGIBLE_ASSETS, SWITCHING_COSTS,
 *   NETWORK_EFFECT, COST_ADVANTAGE) on the analysis page.
 * - Visible only to authenticated users (auth gate at component level so
 *   the wrapper analysis page does not need to know about it).
 * - Persists changes on blur (status select + note textarea).
 *
 * Reference: design_&_architecture/components/frontend-components.md
 *   §moat/MoatChecklist.
 * Reference: management/kanban/.../US-016.md §AC + §Business Rules.
 */
const MOAT_ORDER: ReadonlyArray<MoatType> = [
  'INTANGIBLE_ASSETS',
  'SWITCHING_COSTS',
  'NETWORK_EFFECT',
  'COST_ADVANTAGE',
];

const STATUS_OPTIONS: ReadonlyArray<MoatStatus> = ['PRESENT', 'PARTIAL', 'ABSENT'];

interface RowState {
  status: MoatStatus | '';
  note: string;
  dirty: boolean;
  saving: boolean;
  error: string | null;
}

function emptyRow(): RowState {
  return { status: '', note: '', dirty: false, saving: false, error: null };
}

function rowFromEntry(entry: MoatChecklistEntry): RowState {
  return {
    status: entry.status ?? '',
    note: entry.note ?? '',
    dirty: false,
    saving: false,
    error: null,
  };
}

export function MoatChecklist({
  ticker,
}: {
  readonly ticker: string;
}): React.ReactElement | null {
  const accessToken = useAuthStore((s) => s.accessToken);
  const [rows, setRows] = useState<Record<MoatType, RowState>>({
    INTANGIBLE_ASSETS: emptyRow(),
    SWITCHING_COSTS: emptyRow(),
    NETWORK_EFFECT: emptyRow(),
    COST_ADVANTAGE: emptyRow(),
  });
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!accessToken) return;
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    fetchMoatChecklist(ticker)
      .then((checklist) => {
        if (cancelled) return;
        setRows((prev) => {
          const next = { ...prev };
          for (const entry of checklist.entries) {
            next[entry.moatType] = rowFromEntry(entry);
          }
          return next;
        });
      })
      .catch((err: unknown) => {
        if (cancelled) return;
        setLoadError(
          toUserMessage(err, {
            fallback: 'Impossibile caricare la checklist moat. Riprova.',
          }),
        );
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, ticker]);

  if (!accessToken) return null;

  function updateRow(type: MoatType, patch: Partial<RowState>): void {
    setRows((prev) => ({ ...prev, [type]: { ...prev[type], ...patch } }));
  }

  async function persistRow(type: MoatType): Promise<void> {
    const row = rows[type];
    if (!row.dirty || row.status === '') return;
    updateRow(type, { saving: true, error: null });
    try {
      await upsertMoatEntry(ticker, {
        moatType: type,
        status: row.status,
        note: row.note || null,
      });
      updateRow(type, { saving: false, dirty: false });
    } catch (err) {
      updateRow(type, {
        saving: false,
        error: toUserMessage(err, {
          fallback: 'Salvataggio non riuscito. Riprova.',
        }),
      });
    }
  }

  return (
    <Card
      className="p-4"
      data-testid="moat-checklist"
      aria-busy={loading}
    >
      <h2 className="mb-2 text-lg font-semibold">Checklist Moat</h2>
      <p className="mb-4 text-xs text-slate-500">
        Annotazione qualitativa personale: non altera i semafori dell&apos;analisi.
      </p>
      {loadError && (
        <p role="alert" className="mb-3 text-sm text-red-600">
          {loadError}
        </p>
      )}
      {loading && !loadError && (
        <div
          className="mb-3 grid gap-4 sm:grid-cols-2"
          data-testid="moat-checklist-skeleton"
          role="status"
        >
          {MOAT_ORDER.map((type) => (
            <div
              key={`skeleton-${type}`}
              className="animate-pulse rounded-md border border-slate-200 p-3 dark:border-slate-700"
              aria-hidden="true"
            >
              <div className="mb-2 h-4 w-32 rounded bg-slate-200 dark:bg-slate-700" />
              <div className="mb-3 h-3 w-full rounded bg-slate-100 dark:bg-slate-800" />
              <div className="mb-2 h-9 w-full rounded bg-slate-100 dark:bg-slate-800" />
              <div className="h-16 w-full rounded bg-slate-100 dark:bg-slate-800" />
            </div>
          ))}
          <span className="sr-only">Caricamento checklist moat…</span>
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2">
        {MOAT_ORDER.map((type) => {
          const row = rows[type];
          return (
            <fieldset
              key={type}
              className="rounded-md border border-slate-200 p-3 dark:border-slate-700"
              data-testid={`moat-${type}`}
            >
              <legend className="px-1 text-sm font-medium">
                {MOAT_TYPE_LABELS[type]}
              </legend>
              <p className="mb-2 text-xs text-slate-500">
                {MOAT_TYPE_DESCRIPTIONS[type]}
              </p>
              <label className="mb-2 flex flex-col gap-1 text-sm">
                <span>Stato</span>
                <select
                  value={row.status}
                  onChange={(e) =>
                    updateRow(type, {
                      status: e.target.value as MoatStatus | '',
                      dirty: true,
                    })
                  }
                  onBlur={() => void persistRow(type)}
                  disabled={loading || row.saving}
                  className="h-9 rounded-md border border-slate-300 bg-white px-2 text-sm dark:border-slate-700 dark:bg-slate-900"
                  data-testid={`moat-status-${type}`}
                >
                  <option value="">— Seleziona —</option>
                  {STATUS_OPTIONS.map((status) => (
                    <option key={status} value={status}>
                      {MOAT_STATUS_LABELS[status]}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1 text-sm">
                <span>Nota</span>
                <textarea
                  rows={3}
                  value={row.note}
                  onChange={(e) =>
                    updateRow(type, { note: e.target.value, dirty: true })
                  }
                  onBlur={() => void persistRow(type)}
                  disabled={loading || row.saving}
                  className="rounded-md border border-slate-300 bg-white p-2 text-sm dark:border-slate-700 dark:bg-slate-900"
                  data-testid={`moat-note-${type}`}
                  maxLength={4000}
                />
              </label>
              {row.saving && (
                <p className="mt-1 text-xs text-slate-500">Salvataggio…</p>
              )}
              {row.error && (
                <p role="alert" className="mt-1 text-xs text-red-600">
                  {row.error}
                </p>
              )}
            </fieldset>
          );
        })}
      </div>
    </Card>
  );
}
