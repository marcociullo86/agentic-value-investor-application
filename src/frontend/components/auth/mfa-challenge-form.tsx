'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { FormErrorSummary } from '@/components/forms/form-error-summary';
import { FormField } from '@/components/forms/form-field';
import { useAuthStore } from '@/lib/stores/useAuthStore';
import { getAuthFormErrorMessage } from '@/app/(auth)/_lib/form-errors';

/**
 * MfaChallengeForm (TSK-233 — US-081 / ADR-025 §4).
 *
 * Renders the second factor during login when the BE returned
 * `{ mfaRequired: true, mfaToken }`. Two modes share the same card:
 *  - TOTP: 6-digit code from the authenticator app
 *    → POST /api/auth/mfa/challenge.
 *  - Recovery: one-time recovery code (alternative if the user lost
 *    access to the authenticator) → POST /api/auth/mfa/recovery.
 *
 * The component delegates token finalization to the auth store
 * (`completeMfaChallenge` / `completeMfaRecovery`) so the post-login
 * cookie/state shape is byte-identical to the no-MFA path.
 *
 * A11y: every input is wrapped in `FormField` with a programmatic label
 * and `aria-describedby` linked error; the page-level error summary
 * focuses itself on submit failure (`role="alert"`, `aria-live="assertive"`).
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
 */

const totpSchema = z.object({
  totpCode: z
    .string()
    .min(1, 'Inserisci il codice a 6 cifre')
    .regex(/^\d{6}$/u, 'Il codice deve essere di 6 cifre'),
});

const recoverySchema = z.object({
  recoveryCode: z
    .string()
    .min(1, 'Inserisci un codice di recupero'),
});

type TotpFormValues = z.infer<typeof totpSchema>;
type RecoveryFormValues = z.infer<typeof recoverySchema>;

const TOTP_FIELD_LABELS: Record<string, string> = { totpCode: 'Codice TOTP' };
const RECOVERY_FIELD_LABELS: Record<string, string> = {
  recoveryCode: 'Codice di recupero',
};

export interface MfaChallengeFormProps {
  /** Short-lived JWT (~5 min) issued by `POST /api/auth/login`. */
  readonly mfaToken: string;
  /**
   * Email submitted to /login — propagated into the auth-store user
   * placeholder so the rest of the app sees the same identity it would
   * after a non-MFA login.
   */
  readonly email: string;
  /** Called after the challenge succeeds and the access token is stored. */
  readonly onSuccess: () => void;
}

export function MfaChallengeForm({
  mfaToken,
  email,
  onSuccess,
}: MfaChallengeFormProps): React.ReactElement {
  const completeMfaChallenge = useAuthStore((s) => s.completeMfaChallenge);
  const completeMfaRecovery = useAuthStore((s) => s.completeMfaRecovery);

  const [mode, setMode] = useState<'totp' | 'recovery'>('totp');
  const [serverError, setServerError] = useState<string | null>(null);

  const totpForm = useForm<TotpFormValues>({
    resolver: zodResolver(totpSchema),
    mode: 'onSubmit',
  });

  const recoveryForm = useForm<RecoveryFormValues>({
    resolver: zodResolver(recoverySchema),
    mode: 'onSubmit',
  });

  async function onTotpSubmit(data: TotpFormValues): Promise<void> {
    setServerError(null);
    try {
      await completeMfaChallenge(mfaToken, data.totpCode, email);
      onSuccess();
    } catch (err) {
      setServerError(getAuthFormErrorMessage(err, 'login'));
    }
  }

  async function onRecoverySubmit(data: RecoveryFormValues): Promise<void> {
    setServerError(null);
    try {
      await completeMfaRecovery(mfaToken, data.recoveryCode.trim(), email);
      onSuccess();
    } catch (err) {
      setServerError(getAuthFormErrorMessage(err, 'login'));
    }
  }

  function switchMode(next: 'totp' | 'recovery'): void {
    setMode(next);
    setServerError(null);
  }

  return (
    <div data-testid="mfa-challenge-form">
      <h2 className="mb-2 text-xl font-semibold">Verifica in due passaggi</h2>
      <p className="mb-4 text-sm text-on-surface/70">
        {mode === 'totp'
          ? 'Inserisci il codice a 6 cifre generato dalla tua app di autenticazione.'
          : 'Inserisci uno dei codici di recupero salvati durante l\'attivazione di MFA.'}
      </p>

      {mode === 'totp' ? (
        <form
          className="flex flex-col gap-4"
          onSubmit={totpForm.handleSubmit(onTotpSubmit)}
          noValidate
        >
          <FormErrorSummary
            errors={totpForm.formState.errors}
            fieldLabels={TOTP_FIELD_LABELS}
          />

          {serverError && (
            <div
              role="alert"
              className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
              data-testid="mfa-challenge-error"
            >
              {serverError}
            </div>
          )}

          <FormField
            name="totpCode"
            label="Codice TOTP"
            error={totpForm.formState.errors.totpCode?.message}
          >
            <Input
              id="totpCode"
              type="text"
              inputMode="numeric"
              autoComplete="one-time-code"
              autoFocus
              maxLength={6}
              pattern="[0-9]{6}"
              error={!!totpForm.formState.errors.totpCode}
              aria-describedby={
                totpForm.formState.errors.totpCode ? 'totpCode-error' : undefined
              }
              data-testid="mfa-totp-input"
              {...totpForm.register('totpCode')}
            />
          </FormField>

          <Button
            type="submit"
            disabled={totpForm.formState.isSubmitting}
            data-testid="mfa-totp-submit"
          >
            {totpForm.formState.isSubmitting ? 'Verifica…' : 'Verifica'}
          </Button>

          <button
            type="button"
            className="text-center text-sm text-blue-600 hover:underline"
            onClick={() => switchMode('recovery')}
            data-testid="mfa-use-recovery"
          >
            Usa un codice di recupero
          </button>
        </form>
      ) : (
        <form
          className="flex flex-col gap-4"
          onSubmit={recoveryForm.handleSubmit(onRecoverySubmit)}
          noValidate
        >
          <FormErrorSummary
            errors={recoveryForm.formState.errors}
            fieldLabels={RECOVERY_FIELD_LABELS}
          />

          {serverError && (
            <div
              role="alert"
              className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
              data-testid="mfa-challenge-error"
            >
              {serverError}
            </div>
          )}

          <FormField
            name="recoveryCode"
            label="Codice di recupero"
            error={recoveryForm.formState.errors.recoveryCode?.message}
          >
            <Input
              id="recoveryCode"
              type="text"
              autoComplete="one-time-code"
              autoFocus
              error={!!recoveryForm.formState.errors.recoveryCode}
              aria-describedby={
                recoveryForm.formState.errors.recoveryCode
                  ? 'recoveryCode-error'
                  : undefined
              }
              data-testid="mfa-recovery-input"
              {...recoveryForm.register('recoveryCode')}
            />
          </FormField>

          <Button
            type="submit"
            disabled={recoveryForm.formState.isSubmitting}
            data-testid="mfa-recovery-submit"
          >
            {recoveryForm.formState.isSubmitting ? 'Verifica…' : 'Verifica'}
          </Button>

          <button
            type="button"
            className="text-center text-sm text-blue-600 hover:underline"
            onClick={() => switchMode('totp')}
            data-testid="mfa-use-totp"
          >
            Usa il codice TOTP
          </button>
        </form>
      )}
    </div>
  );
}
