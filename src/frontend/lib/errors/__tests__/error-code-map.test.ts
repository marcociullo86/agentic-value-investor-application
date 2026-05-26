import { describe, it, expect } from 'vitest';
import { getErrorMessage, getErrorI18n } from '../error-code-map';
import locale from '@/locales/it.json';

const ALL_TYPE_URIS = [
  'urn:problem-type:validation-failed',
  'urn:problem-type:invalid-credentials',
  'urn:problem-type:unauthorized',
  'urn:problem-type:forbidden',
  'urn:problem-type:not-found',
  'urn:problem-type:email-already-registered',
  'urn:problem-type:server-error',
  'urn:problem-type:fmp-unavailable',
] as const;

const TYPES_WITH_CTA = [
  'urn:problem-type:validation-failed',
  'urn:problem-type:invalid-credentials',
  'urn:problem-type:unauthorized',
  'urn:problem-type:email-already-registered',
  'urn:problem-type:server-error',
  'urn:problem-type:fmp-unavailable',
] as const;

const TYPES_WITHOUT_CTA = [
  'urn:problem-type:forbidden',
  'urn:problem-type:not-found',
] as const;

describe('errorCodeMap — mapping 8 codici', () => {
  it.each(ALL_TYPE_URIS)(
    'getErrorMessage(%s) returns a valid messageKey',
    (type) => {
      const result = getErrorMessage(type);
      expect(result.messageKey).toBeTruthy();
      expect(result.messageKey).not.toBe('errors.generic');
    },
  );

  it.each(ALL_TYPE_URIS)(
    'getErrorI18n(%s) returns non-empty title and message',
    (type) => {
      const result = getErrorI18n(type);
      expect(result.title).toBeTruthy();
      expect(result.message).toBeTruthy();
      expect(result.title.length).toBeGreaterThan(0);
      expect(result.message.length).toBeGreaterThan(0);
    },
  );
});

describe('errorCodeMap — CTA', () => {
  it.each(TYPES_WITH_CTA)(
    '%s has a CTA',
    (type) => {
      const msgResult = getErrorMessage(type);
      expect(msgResult.ctaKey).toBeDefined();

      const i18nResult = getErrorI18n(type);
      expect(i18nResult.cta).toBeTruthy();
    },
  );

  it.each(TYPES_WITHOUT_CTA)(
    '%s has no CTA',
    (type) => {
      const msgResult = getErrorMessage(type);
      expect(msgResult.ctaKey).toBeUndefined();

      const i18nResult = getErrorI18n(type);
      expect(i18nResult.cta).toBeUndefined();
    },
  );
});

describe('errorCodeMap — fallback generico', () => {
  it('unmapped code returns generic localized message', () => {
    const result = getErrorI18n('urn:problem-type:unknown-xyz');
    expect(result.title).toBe(locale.errors.generic.title);
    expect(result.message).toBe(locale.errors.generic.message);
  });

  it('getErrorMessage returns fallback key for unmapped code', () => {
    const result = getErrorMessage('urn:problem-type:unknown-xyz');
    expect(result.messageKey).toBe('errors.generic');
  });
});

describe('errorCodeMap — fallback con correlationId', () => {
  it('unmapped code with correlationId includes the ID in the message', () => {
    const correlationId = 'abc-123-def';
    const result = getErrorI18n('urn:problem-type:unknown-xyz', correlationId);

    expect(result.title).toBe(locale.errors.genericWithCorrelation.title);
    expect(result.message).toContain(correlationId);
    expect(result.message).not.toContain('{{correlationId}}');
  });

  it('mapped code ignores correlationId', () => {
    const result = getErrorI18n(
      'urn:problem-type:server-error',
      'some-corr-id',
    );
    expect(result.message).not.toContain('some-corr-id');
    expect(result.message).toBe(locale.errors.serverError.message);
  });
});

describe('errorCodeMap — anti-raw HTTP codes', () => {
  const HTTP_RAW_PATTERNS = ['400', '401', '403', '404', '500'];

  const ANTI_RAW_SCENARIOS = [
    'urn:problem-type:validation-failed',
    'urn:problem-type:unauthorized',
    'urn:problem-type:forbidden',
    'urn:problem-type:not-found',
    'urn:problem-type:server-error',
  ] as const;

  it.each(ANTI_RAW_SCENARIOS)(
    'getErrorI18n(%s) strings contain no raw HTTP status codes',
    (type) => {
      const result = getErrorI18n(type);
      const allStrings = [result.title, result.message, result.cta]
        .filter(Boolean)
        .join(' ');

      for (const code of HTTP_RAW_PATTERNS) {
        expect(allStrings).not.toContain(code);
      }
    },
  );
});

describe('errorCodeMap — i18n source verification', () => {
  const KEY_TO_ENTRY: Record<string, string> = {
    'urn:problem-type:validation-failed': 'validationFailed',
    'urn:problem-type:invalid-credentials': 'invalidCredentials',
    'urn:problem-type:unauthorized': 'unauthorized',
    'urn:problem-type:forbidden': 'forbidden',
    'urn:problem-type:not-found': 'notFound',
    'urn:problem-type:email-already-registered': 'emailAlreadyRegistered',
    'urn:problem-type:server-error': 'serverError',
    'urn:problem-type:fmp-unavailable': 'fmpUnavailable',
  };

  it.each(Object.entries(KEY_TO_ENTRY))(
    'getErrorI18n(%s) title and message match locales/it.json entry "%s"',
    (type, localeKey) => {
      const result = getErrorI18n(type);
      const expected = locale.errors[localeKey as keyof typeof locale.errors];

      expect(result.title).toBe(expected.title);
      expect(result.message).toBe(expected.message);

      if ('cta' in expected) {
        expect(result.cta).toBe(expected.cta);
      } else {
        expect(result.cta).toBeUndefined();
      }
    },
  );

  it('all error keys in locale file have a corresponding type URI mapping', () => {
    const nonMappedKeys = ['generic', 'genericWithCorrelation', 'offline', 'timeout'];
    const localeErrorKeys = Object.keys(locale.errors).filter(
      (k) => !nonMappedKeys.includes(k),
    );

    const mappedLocaleKeys = Object.values(KEY_TO_ENTRY);

    for (const localeKey of localeErrorKeys) {
      expect(mappedLocaleKeys).toContain(localeKey);
    }
  });
});
