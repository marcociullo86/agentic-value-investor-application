import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { toUserMessage } from '../to-user-message';

function makeAxiosError(opts: {
  status?: number;
  data?: unknown;
}): AxiosError {
  const err = new AxiosError(
    'Request failed with status code ' + (opts.status ?? 'unknown'),
    'ERR_BAD_REQUEST',
  );
  if (opts.status !== undefined || opts.data !== undefined) {
    err.response = {
      status: opts.status ?? 500,
      statusText: '',
      headers: {},
      config: { headers: new AxiosHeaders() },
      data: opts.data,
    };
  }
  return err;
}

describe('toUserMessage', () => {
  it('returns generic fallback for non-axios errors', () => {
    expect(toUserMessage(new Error('boom'))).not.toContain('boom');
    expect(toUserMessage(new Error('boom'))).toMatch(/imprevisto/i);
  });

  it('uses custom fallback for non-axios errors when provided', () => {
    expect(
      toUserMessage(new Error('boom'), { fallback: 'Aggiunta fallita' }),
    ).toBe('Aggiunta fallita');
  });

  it('returns network fallback for axios error without response', () => {
    const err = new AxiosError('Network Error', 'ERR_NETWORK');
    const msg = toUserMessage(err);
    expect(msg).toMatch(/rete|connessione/i);
    expect(msg).not.toContain('Network Error');
  });

  it('maps 401 to user-safe session expired copy', () => {
    expect(toUserMessage(makeAxiosError({ status: 401 }))).toMatch(/sessione|accesso/i);
  });

  it('maps 403 to user-safe permission copy', () => {
    expect(toUserMessage(makeAxiosError({ status: 403 }))).toMatch(/permess/i);
  });

  it('maps 404 to user-safe not-found copy', () => {
    expect(toUserMessage(makeAxiosError({ status: 404 }))).toMatch(/non trovat/i);
  });

  it('maps 429 to user-safe rate limit copy', () => {
    expect(toUserMessage(makeAxiosError({ status: 429 }))).toMatch(/troppe|riprova/i);
  });

  it('maps 5xx to user-safe server-error copy', () => {
    const msg = toUserMessage(makeAxiosError({ status: 503 }));
    expect(msg).toMatch(/servizio|server/i);
    expect(msg).not.toContain('503');
  });

  it('applies statusOverrides before built-in mapping', () => {
    expect(
      toUserMessage(makeAxiosError({ status: 409 }), {
        statusOverrides: { 409: 'Ticker già presente in watchlist.' },
      }),
    ).toBe('Ticker già presente in watchlist.');
  });

  it('uses ProblemDetail type i18n when available', () => {
    const err = makeAxiosError({
      status: 503,
      data: { type: 'urn:problem-type:fmp-unavailable' },
    });
    const msg = toUserMessage(err);
    expect(msg).toMatch(/servizio|non disponibile/i);
    expect(msg).not.toContain('503');
  });

  it('never exposes raw HTTP status codes from Axios message', () => {
    const err = makeAxiosError({ status: 500 });
    const msg = toUserMessage(err);
    expect(msg).not.toContain('500');
    expect(msg).not.toMatch(/Request failed/i);
  });

  it('never exposes raw Error.message from non-axios errors', () => {
    const sensitive = 'TypeError: Cannot read properties of undefined';
    const msg = toUserMessage(new Error(sensitive));
    expect(msg).not.toContain('TypeError');
    expect(msg).not.toContain('undefined');
  });
});
