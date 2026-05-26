'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { triggerTopPicksRun } from '@/lib/api/top-picks';

type FeedbackKind = 'info' | 'success' | 'warning' | 'error';

interface Feedback {
  readonly kind: FeedbackKind;
  readonly text: string;
}

export function TopPicksManualTriggerButton(): React.ReactElement {
  const [isPending, setIsPending] = useState(false);
  const [feedback, setFeedback] = useState<Feedback | null>(null);

  async function onClick(): Promise<void> {
    setIsPending(true);
    setFeedback(null);
    try {
      const { httpStatus, body } = await triggerTopPicksRun();
      if (httpStatus === 202) {
        setFeedback({ kind: 'success', text: body.message });
      } else if (httpStatus === 409) {
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
      setFeedback({
        kind: 'error',
        text: `Impossibile avviare il job: ${message}`,
      });
    } finally {
      setIsPending(false);
    }
  }

  return (
    <div className="flex flex-col items-center gap-2">
      <Button
        type="button"
        variant="secondary"
        size="md"
        onClick={() => void onClick()}
        disabled={isPending}
        data-testid="top-picks-manual-trigger"
      >
        {isPending ? 'Avvio in corso…' : 'Lancia batch Top Picks ora'}
      </Button>
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
