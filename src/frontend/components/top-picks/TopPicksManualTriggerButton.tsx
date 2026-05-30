'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { Button } from '@/components/ui/Button';
import {
  cancelTopPicksRun,
  getTopPicksRunStatus,
  triggerTopPicksRun,
} from '@/lib/api/top-picks';

type FeedbackKind = 'info' | 'success' | 'warning' | 'error';

interface Feedback {
  readonly kind: FeedbackKind;
  readonly text: string;
}

// Intervallo di polling dello stato run mentre un batch è in corso: il job può
// durare 10-30 min, ogni 5s è abbastanza per far ritornare la UI a "Lancia"
// poco dopo il termine (naturale o per blocco) senza martellare il backend.
const STATUS_POLL_MS = 5_000;

/**
 * Controllo manuale del batch Top Picks in homepage.
 *
 * Due stati mutuamente esclusivi:
 *  - idle    → bottone "Lancia batch Top Picks ora" (POST /run).
 *  - running → bottone "Blocca batch Top Picks" (POST /run/cancel).
 *
 * Lo stato `running` è derivato dal backend (GET /run/status) al mount e via
 * polling mentre il batch gira, così sopravvive a un reload di pagina e torna
 * a idle quando il job termina (anche se avviato da un altro client/tab).
 */
export function TopPicksManualTriggerButton(): React.ReactElement {
  const [isRunning, setIsRunning] = useState(false);
  const [isPending, setIsPending] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  // Distingue "terminato naturalmente" da "bloccato" quando lo stato passa
  // running → idle: il polling vede solo `running=false`, non il motivo.
  const cancelRequestedRef = useRef(false);
  // Specchio sincrono di `isRunning` per il polling: confronta il valore
  // precedente senza annidare setState (e senza ri-creare il poller a ogni
  // cambio di stato).
  const isRunningRef = useRef(false);

  const refreshStatus = useCallback(async (): Promise<void> => {
    try {
      const { running } = await getTopPicksRunStatus();
      if (isRunningRef.current && !running) {
        setFeedback(
          cancelRequestedRef.current
            ? { kind: 'info', text: 'Batch bloccato.' }
            : { kind: 'success', text: 'Batch terminato. Risultati su /top-picks.' },
        );
        cancelRequestedRef.current = false;
      }
      isRunningRef.current = running;
      setIsRunning(running);
    } catch {
      // Status best-effort: un errore di rete transitorio non deve cambiare
      // lo stato visibile né mostrare un errore rumoroso al mount.
    }
  }, []);

  // Allinea lo stato al mount, poi continua a fare polling solo mentre gira.
  useEffect(() => {
    void refreshStatus();
  }, [refreshStatus]);

  useEffect(() => {
    if (!isRunning) return;
    const id = setInterval(() => void refreshStatus(), STATUS_POLL_MS);
    return () => clearInterval(id);
  }, [isRunning, refreshStatus]);

  const onStart = useCallback(async (): Promise<void> => {
    setIsPending(true);
    setFeedback(null);
    cancelRequestedRef.current = false;
    try {
      const { httpStatus, body } = await triggerTopPicksRun();
      if (httpStatus === 202) {
        isRunningRef.current = true;
        setIsRunning(true);
        setFeedback({ kind: 'success', text: body.message });
      } else if (httpStatus === 409) {
        // Un run è già in corso (es. avviato da un altro tab): mostra Blocca.
        isRunningRef.current = true;
        setIsRunning(true);
        setFeedback({ kind: 'warning', text: body.message });
      } else {
        setFeedback({
          kind: 'info',
          text: body.message ?? `Risposta inattesa (HTTP ${httpStatus}).`,
        });
      }
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Errore di rete sconosciuto.';
      setFeedback({ kind: 'error', text: `Impossibile avviare il job: ${message}` });
    } finally {
      setIsPending(false);
    }
  }, []);

  const onCancel = useCallback(async (): Promise<void> => {
    setIsPending(true);
    setFeedback(null);
    try {
      const { httpStatus, body } = await cancelTopPicksRun();
      if (httpStatus === 202) {
        // Cancellazione cooperativa: il job si ferma al prossimo ticker. Il
        // polling rileverà running=false e mostrerà "Batch bloccato.".
        cancelRequestedRef.current = true;
        setFeedback({ kind: 'warning', text: body.message });
        void refreshStatus();
      } else if (httpStatus === 409) {
        // Nessun run in corso (terminato nel frattempo): riallinea a idle.
        cancelRequestedRef.current = false;
        isRunningRef.current = false;
        setIsRunning(false);
        setFeedback({ kind: 'info', text: body.message });
      } else {
        setFeedback({
          kind: 'info',
          text: body.message ?? `Risposta inattesa (HTTP ${httpStatus}).`,
        });
      }
    } catch (err) {
      const message =
        err instanceof Error ? err.message : 'Errore di rete sconosciuto.';
      setFeedback({ kind: 'error', text: `Impossibile bloccare il job: ${message}` });
    } finally {
      setIsPending(false);
    }
  }, [refreshStatus]);

  return (
    <div className="flex flex-col items-center gap-2">
      {isRunning ? (
        <Button
          type="button"
          variant="destructive"
          size="md"
          onClick={() => void onCancel()}
          disabled={isPending}
          data-testid="top-picks-manual-cancel"
        >
          {isPending ? 'Blocco in corso…' : 'Blocca batch Top Picks'}
        </Button>
      ) : (
        <Button
          type="button"
          variant="secondary"
          size="md"
          onClick={() => void onStart()}
          disabled={isPending}
          data-testid="top-picks-manual-trigger"
        >
          {isPending ? 'Avvio in corso…' : 'Lancia batch Top Picks ora'}
        </Button>
      )}
      {feedback !== null ? (
        <p
          role="status"
          aria-live="polite"
          className={
            feedback.kind === 'success'
              ? 'text-xs text-green-700 dark:text-green-400'
              : feedback.kind === 'warning'
                ? 'text-xs text-amber-700 dark:text-amber-400'
                : feedback.kind === 'error'
                  ? 'text-xs text-red-700 dark:text-red-400'
                  : 'text-xs text-slate-600 dark:text-slate-400'
          }
        >
          {feedback.text}
        </p>
      ) : null}
    </div>
  );
}
