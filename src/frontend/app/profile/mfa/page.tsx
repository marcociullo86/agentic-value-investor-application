'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Input } from '@/components/ui/Input';
import { Card } from '@/components/ui/Card';
import { FormErrorSummary } from '@/components/forms/form-error-summary';
import { FormField } from '@/components/forms/form-field';
import { AuthGuard } from '@/components/auth/AuthGuard';
import {
  enrollMfa,
  verifyMfa,
  disableMfa,
  type MfaEnrollmentResponse,
} from '@/lib/api/auth';
import { toUserMessage } from '@/lib/to-user-message';

/**
 * MfaEnrollmentPage (TSK-232 — US-081 / ADR-025 §4).
 *
 * Three-step enrollment funnel + a fourth disable section, all rendered
 * conditionally on a single page:
 *
 *   1. INTRO  → "Attiva MFA" button → POST /api/auth/mfa/enroll →
 *               receives { secret, qrCodeUri, recoveryCodes }.
 *   2. VERIFY → display the otpauth provisioning URI + the manual
 *               base32 secret + a 6-digit TOTP input → POST
 *               /api/auth/mfa/verify (204 No Content on success).
 *   3. CODES  → display the 8 plain-text recovery codes ONCE; require
 *               an explicit "Ho salvato i codici" confirmation before
 *               redirecting to the profile.
 *   4. DISABLE → password-confirm form → DELETE /api/auth/mfa.
 *
 * This page does NOT render an inline QR image: the project does not
 * ship a QR rendering library (no `qrcode` / `qrcode.react` in
 * `package.json`) and the user-facing instructions explicitly accept
 * a "lightweight otpauth URI display" fallback. Most authenticator
 * apps (Google Authenticator, Authy, 1Password, Bitwarden, …) accept
 * either pasting the otpauth URI or typing the base32 secret manually,
 * so the flow remains scannable end-to-end without a JS dependency.
 *
 * AuthGuard wraps the page so unauthenticated users are bounced to
 * /login by the existing rehydration-aware client guard (TSK-211).
 *
 * [^src: design_&_architecture/decisions/ADR-025-security-hardening-pci-dss.md §4]
 */

type Stage = 'intro' | 'verify' | 'codes';

const totpSchema = z.object({
  totpCode: z
    .string()
    .min(1, 'Inserisci il codice a 6 cifre')
    .regex(/^\d{6}$/u, 'Il codice deve essere di 6 cifre'),
});
type TotpFormValues = z.infer<typeof totpSchema>;

const TOTP_FIELD_LABELS: Record<string, string> = { totpCode: 'Codice TOTP' };

const disableSchema = z.object({
  password: z.string().min(1, 'Inserisci la password per confermare'),
});
type DisableFormValues = z.infer<typeof disableSchema>;
const DISABLE_FIELD_LABELS: Record<string, string> = { password: 'Password' };

export default function MfaEnrollmentPage(): React.ReactElement {
  return (
    <AuthGuard
      fallback={
        <main className="mx-auto flex min-h-screen max-w-2xl items-center justify-center px-6">
          <p className="text-sm text-on-surface/60">Caricamento…</p>
        </main>
      }
    >
      <MfaEnrollmentContent />
    </AuthGuard>
  );
}

function MfaEnrollmentContent(): React.ReactElement {
  const router = useRouter();

  const [stage, setStage] = useState<Stage>('intro');
  const [enrollment, setEnrollment] = useState<MfaEnrollmentResponse | null>(
    null,
  );
  const [enrollError, setEnrollError] = useState<string | null>(null);
  const [enrolling, setEnrolling] = useState(false);

  const verifyForm = useForm<TotpFormValues>({
    resolver: zodResolver(totpSchema),
    mode: 'onSubmit',
  });
  const [verifyError, setVerifyError] = useState<string | null>(null);

  const disableForm = useForm<DisableFormValues>({
    resolver: zodResolver(disableSchema),
    mode: 'onSubmit',
  });
  const [disableError, setDisableError] = useState<string | null>(null);
  const [disableSuccess, setDisableSuccess] = useState<string | null>(null);

  async function handleEnroll(): Promise<void> {
    setEnrollError(null);
    setEnrolling(true);
    try {
      const result = await enrollMfa();
      setEnrollment(result);
      setStage('verify');
    } catch (err) {
      setEnrollError(
        toUserMessage(err, {
          fallback: 'Avvio enrollment MFA non riuscito. Riprova.',
          statusOverrides: {
            409: 'MFA è già attivo su questo account.',
          },
        }),
      );
    } finally {
      setEnrolling(false);
    }
  }

  async function handleVerify(data: TotpFormValues): Promise<void> {
    setVerifyError(null);
    try {
      await verifyMfa({ totpCode: data.totpCode });
      setStage('codes');
    } catch (err) {
      setVerifyError(
        toUserMessage(err, {
          fallback: 'Verifica del codice non riuscita. Riprova.',
          statusOverrides: {
            400: 'Codice TOTP non valido. Controlla l\'app di autenticazione.',
            409: 'MFA è già attivo o la sessione di enrollment è scaduta.',
          },
        }),
      );
    }
  }

  async function handleDisable(data: DisableFormValues): Promise<void> {
    setDisableError(null);
    setDisableSuccess(null);
    try {
      await disableMfa({ password: data.password });
      setDisableSuccess('MFA disattivato.');
      disableForm.reset();
    } catch (err) {
      setDisableError(
        toUserMessage(err, {
          fallback: 'Disattivazione MFA non riuscita. Riprova.',
          statusOverrides: {
            401: 'Password non corretta.',
            409: 'MFA non risulta attivo.',
          },
        }),
      );
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-2xl flex-col gap-6 px-6 py-8">
      <h1 className="text-2xl font-bold">Autenticazione a due fattori (MFA)</h1>

      <Card className="p-6" data-testid="mfa-enroll-card">
        {stage === 'intro' && (
          <IntroStage
            enrolling={enrolling}
            error={enrollError}
            onEnroll={handleEnroll}
          />
        )}

        {stage === 'verify' && enrollment && (
          <VerifyStage
            enrollment={enrollment}
            form={verifyForm}
            serverError={verifyError}
            onSubmit={handleVerify}
          />
        )}

        {stage === 'codes' && enrollment && (
          <CodesStage
            recoveryCodes={enrollment.recoveryCodes}
            onConfirm={() => router.push('/')}
          />
        )}
      </Card>

      <Card className="p-6" data-testid="mfa-disable-card">
        <h2 className="mb-2 text-lg font-semibold">Disattiva MFA</h2>
        <p className="mb-4 text-sm text-on-surface/70">
          Per disattivare MFA conferma la tua password.
        </p>
        <form
          className="flex flex-col gap-4"
          onSubmit={disableForm.handleSubmit(handleDisable)}
          noValidate
        >
          <FormErrorSummary
            errors={disableForm.formState.errors}
            fieldLabels={DISABLE_FIELD_LABELS}
          />

          {disableError && (
            <div
              role="alert"
              className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
              data-testid="mfa-disable-error"
            >
              {disableError}
            </div>
          )}

          {disableSuccess && (
            <div
              role="status"
              className="rounded-md border border-success/30 bg-success/5 px-3 py-2 text-sm text-success"
              data-testid="mfa-disable-success"
            >
              {disableSuccess}
            </div>
          )}

          <FormField
            name="password"
            label="Password"
            error={disableForm.formState.errors.password?.message}
          >
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              error={!!disableForm.formState.errors.password}
              aria-describedby={
                disableForm.formState.errors.password ? 'password-error' : undefined
              }
              data-testid="mfa-disable-password"
              {...disableForm.register('password')}
            />
          </FormField>

          <Button
            type="submit"
            variant="destructive"
            disabled={disableForm.formState.isSubmitting}
            data-testid="mfa-disable-submit"
          >
            {disableForm.formState.isSubmitting
              ? 'Disattivazione…'
              : 'Disattiva MFA'}
          </Button>
        </form>
      </Card>
    </main>
  );
}

function IntroStage({
  enrolling,
  error,
  onEnroll,
}: {
  readonly enrolling: boolean;
  readonly error: string | null;
  readonly onEnroll: () => void;
}): React.ReactElement {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">Attiva MFA</h2>
      <p className="text-sm text-on-surface/70">
        Aggiungi un secondo fattore di autenticazione tramite app TOTP
        (Google Authenticator, Authy, 1Password, Bitwarden…).
      </p>
      {error && (
        <div
          role="alert"
          className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
          data-testid="mfa-enroll-error"
        >
          {error}
        </div>
      )}
      <Button
        type="button"
        onClick={onEnroll}
        disabled={enrolling}
        data-testid="mfa-enroll-submit"
      >
        {enrolling ? 'Avvio enrollment…' : 'Attiva MFA'}
      </Button>
    </div>
  );
}

function VerifyStage({
  enrollment,
  form,
  serverError,
  onSubmit,
}: {
  readonly enrollment: MfaEnrollmentResponse;
  readonly form: ReturnType<typeof useForm<TotpFormValues>>;
  readonly serverError: string | null;
  readonly onSubmit: (data: TotpFormValues) => Promise<void>;
}): React.ReactElement {
  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">Configura la tua app</h2>
      <ol className="list-decimal pl-6 text-sm text-on-surface/80">
        <li>Apri la tua app di autenticazione (Authy, Google Authenticator, …).</li>
        <li>
          Aggiungi un nuovo account incollando l&apos;URI <em>otpauth</em> qui sotto
          oppure inserendo manualmente il secret base32.
        </li>
        <li>Inserisci il codice a 6 cifre generato dall&apos;app per attivare MFA.</li>
      </ol>

      <div
        className="flex flex-col gap-2 rounded-md border border-outline-variant bg-surface p-3 text-sm"
        data-testid="mfa-otpauth-block"
      >
        <span className="font-medium">URI provisioning (otpauth)</span>
        <code
          className="break-all rounded bg-surface-container px-2 py-1 font-mono text-xs"
          aria-label="otpauth provisioning URI"
          data-testid="mfa-otpauth-uri"
        >
          {enrollment.qrCodeUri}
        </code>
        <span className="font-medium">Secret manuale (base32)</span>
        <code
          className="break-all rounded bg-surface-container px-2 py-1 font-mono text-xs tracking-widest"
          aria-label="TOTP secret base32"
          data-testid="mfa-secret"
        >
          {enrollment.secret}
        </code>
      </div>

      <form
        className="flex flex-col gap-4"
        onSubmit={form.handleSubmit(onSubmit)}
        noValidate
      >
        <FormErrorSummary
          errors={form.formState.errors}
          fieldLabels={TOTP_FIELD_LABELS}
        />

        {serverError && (
          <div
            role="alert"
            className="rounded-md border border-error/30 bg-error/5 px-3 py-2 text-sm text-error"
            data-testid="mfa-verify-error"
          >
            {serverError}
          </div>
        )}

        <FormField
          name="totpCode"
          label="Codice TOTP"
          error={form.formState.errors.totpCode?.message}
        >
          <Input
            id="totpCode"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            autoFocus
            maxLength={6}
            pattern="[0-9]{6}"
            error={!!form.formState.errors.totpCode}
            aria-describedby={
              form.formState.errors.totpCode ? 'totpCode-error' : undefined
            }
            data-testid="mfa-verify-input"
            {...form.register('totpCode')}
          />
        </FormField>

        <Button
          type="submit"
          disabled={form.formState.isSubmitting}
          data-testid="mfa-verify-submit"
        >
          {form.formState.isSubmitting ? 'Verifica…' : 'Verifica e attiva'}
        </Button>
      </form>
    </div>
  );
}

function CodesStage({
  recoveryCodes,
  onConfirm,
}: {
  readonly recoveryCodes: readonly string[];
  readonly onConfirm: () => void;
}): React.ReactElement {
  const [acknowledged, setAcknowledged] = useState(false);

  return (
    <div className="flex flex-col gap-4">
      <h2 className="text-lg font-semibold">Codici di recupero</h2>
      <div
        role="alert"
        className="rounded-md border border-warning/30 bg-warning/10 px-3 py-2 text-sm text-warning"
      >
        Salva questi codici in un luogo sicuro (gestore di password,
        documento cifrato). Verranno mostrati una sola volta. Ogni codice
        è utilizzabile una volta sola in caso di smarrimento del secondo fattore.
      </div>
      <ul
        className="grid grid-cols-1 gap-2 rounded-md border border-outline-variant bg-surface p-3 sm:grid-cols-2"
        aria-label="Codici di recupero MFA"
        data-testid="mfa-recovery-codes"
      >
        {recoveryCodes.map((code) => (
          <li
            key={code}
            className="rounded bg-surface-container px-2 py-1 font-mono text-sm tracking-widest"
          >
            {code}
          </li>
        ))}
      </ul>
      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={acknowledged}
          onChange={(e) => setAcknowledged(e.target.checked)}
          data-testid="mfa-codes-ack"
          className="h-4 w-4"
        />
        Ho salvato i codici di recupero in un luogo sicuro.
      </label>
      <Button
        type="button"
        disabled={!acknowledged}
        onClick={onConfirm}
        data-testid="mfa-codes-confirm"
      >
        Continua
      </Button>
    </div>
  );
}
