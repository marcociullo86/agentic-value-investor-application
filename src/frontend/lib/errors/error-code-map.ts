import locale from '@/locales/it.json';

type ErrorEntry = {
  title: string;
  message: string;
  cta?: string;
};

const errorCodeMap: Record<string, string> = {
  'urn:problem-type:validation-failed': 'errors.validationFailed',
  'urn:problem-type:invalid-credentials': 'errors.invalidCredentials',
  'urn:problem-type:unauthorized': 'errors.unauthorized',
  'urn:problem-type:forbidden': 'errors.forbidden',
  'urn:problem-type:not-found': 'errors.notFound',
  'urn:problem-type:email-already-registered': 'errors.emailAlreadyRegistered',
  'urn:problem-type:server-error': 'errors.serverError',
  'urn:problem-type:fmp-unavailable': 'errors.fmpUnavailable',
};

const FALLBACK_KEY = 'errors.generic';
const FALLBACK_KEY_WITH_CORRELATION = 'errors.genericWithCorrelation';

function resolveI18nEntry(key: string): ErrorEntry {
  const [namespace, entryKey] = key.split('.');
  const section = locale[namespace as keyof typeof locale] as
    | Record<string, ErrorEntry>
    | undefined;

  if (!section || !section[entryKey]) {
    return { title: 'Errore', message: key };
  }

  return section[entryKey];
}

/**
 * Maps a ProblemDetail `type` URI to the corresponding i18n key.
 * Returns a fallback key for unmapped codes.
 */
export function getErrorMessage(type: string): {
  messageKey: string;
  ctaKey?: string;
} {
  const key = errorCodeMap[type] ?? FALLBACK_KEY;
  const entry = resolveI18nEntry(key);

  return {
    messageKey: key,
    ctaKey: entry.cta ? `${key}.cta` : undefined,
  };
}

/**
 * Resolves a ProblemDetail `type` URI to user-facing i18n strings.
 * When the type is unmapped and a correlationId is available,
 * it is interpolated into the generic message.
 */
export function getErrorI18n(
  type: string,
  correlationId?: string,
): { title: string; message: string; cta?: string } {
  const isMapped = type in errorCodeMap;
  const key = isMapped
    ? errorCodeMap[type]
    : correlationId
      ? FALLBACK_KEY_WITH_CORRELATION
      : FALLBACK_KEY;

  const entry = resolveI18nEntry(key);

  let message = entry.message;
  if (!isMapped && correlationId) {
    message = message.replace('{{correlationId}}', correlationId);
  }

  return {
    title: entry.title,
    message,
    cta: entry.cta,
  };
}
