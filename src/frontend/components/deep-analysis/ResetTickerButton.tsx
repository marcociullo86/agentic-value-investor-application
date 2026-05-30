'use client';

import { useState, useCallback } from 'react';
import { Button } from '@/components/ui/Button';
import {
  Modal,
  ModalContent,
  ModalTitle,
  ModalDescription,
} from '@/components/ui/Modal';
import { resetTicker, type TickerResetResult } from '@/lib/api/deep-analysis';

export interface ResetTickerButtonProps {
  readonly ticker: string;
  /** Invocata dopo un reset riuscito (es. per ricaricare lo stato della pagina). */
  readonly onResetDone?: () => void;
}

/**
 * Bottone admin "Reset filing/cache" + popup che richiede la MASTER_PASSWORD.
 * Al submit chiama POST /api/analysis/{ticker}/deep/reset; 403 → password errata.
 * Operazione distruttiva: cancella cache + filing + dati deep-analysis del ticker.
 */
export function ResetTickerButton({
  ticker,
  onResetDone,
}: ResetTickerButtonProps): React.ReactElement {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<TickerResetResult | null>(null);

  const reset = useCallback(() => {
    setPassword('');
    setError(null);
    setResult(null);
    setSubmitting(false);
  }, []);

  const onOpenChange = useCallback(
    (next: boolean) => {
      setOpen(next);
      if (!next) reset();
    },
    [reset],
  );

  const submit = useCallback(async () => {
    setSubmitting(true);
    setError(null);
    try {
      const res = await resetTicker(ticker, password);
      setResult(res);
      onResetDone?.();
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status;
      setError(
        status === 403
          ? 'Master password non valida.'
          : 'Reset fallito. Riprova.',
      );
    } finally {
      setSubmitting(false);
    }
  }, [ticker, password, onResetDone]);

  return (
    <>
      <Button
        type="button"
        variant="destructive"
        size="sm"
        onClick={() => setOpen(true)}
        data-testid="deep-analysis-reset-open"
        title="Cancella cache, filing e dati deep-analysis del ticker (richiede master password)"
      >
        Reset filing/cache
      </Button>

      <Modal open={open} onOpenChange={onOpenChange}>
        <ModalContent data-testid="reset-ticker-modal">
          <ModalTitle className="text-lg font-semibold text-on-surface">
            Reset {ticker}
          </ModalTitle>
          <ModalDescription className="text-sm text-on-surface-variant">
            Operazione distruttiva: cancella cache, filing indicizzati e dati
            deep-analysis di <strong>{ticker}</strong>. Inserisci la master
            password per confermare.
          </ModalDescription>

          {result === null ? (
            <form
              className="flex flex-col gap-3"
              onSubmit={(e) => {
                e.preventDefault();
                void submit();
              }}
            >
              <input
                type="password"
                autoComplete="off"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="MASTER_PASSWORD"
                aria-label="Master password"
                data-testid="reset-master-password-input"
                className="rounded-md border border-outline-variant bg-surface px-3 py-2 text-sm text-on-surface focus:outline-none focus-visible:ring-2 focus-visible:ring-primary"
              />
              {error !== null ? (
                <p
                  className="text-sm text-error"
                  role="alert"
                  data-testid="reset-error"
                >
                  {error}
                </p>
              ) : null}
              <div className="flex justify-end gap-2">
                <Button
                  type="button"
                  variant="ghost"
                  size="sm"
                  onClick={() => onOpenChange(false)}
                >
                  Annulla
                </Button>
                <Button
                  type="submit"
                  variant="destructive"
                  size="sm"
                  disabled={submitting || password.length === 0}
                  data-testid="reset-confirm"
                >
                  {submitting ? 'Reset in corso…' : 'Conferma reset'}
                </Button>
              </div>
            </form>
          ) : (
            <div className="flex flex-col gap-3" data-testid="reset-success">
              <p className="text-sm text-on-surface">
                Reset completato: <strong>{result.totalDeleted}</strong> righe
                cancellate.
              </p>
              <div className="flex justify-end">
                <Button
                  type="button"
                  variant="primary"
                  size="sm"
                  onClick={() => onOpenChange(false)}
                >
                  Chiudi
                </Button>
              </div>
            </div>
          )}
        </ModalContent>
      </Modal>
    </>
  );
}
