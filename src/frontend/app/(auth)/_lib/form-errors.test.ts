import { describe, expect, it } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { getAuthFormErrorMessage } from './form-errors';

function makeAxiosError(opts: {
  status?: number;
  data?: unknown;
  noResponse?: boolean;
}): AxiosError {
  const headers = new AxiosHeaders();
  const config = { headers };
  const error = new AxiosError(
    'Request failed',
    'ERR_BAD_REQUEST',
    config as never,
    undefined,
    opts.noResponse
      ? undefined
      : {
          status: opts.status ?? 500,
          statusText: 'X',
          headers: {},
          config: config as never,
          data: opts.data,
        },
  );
  return error;
}

describe('getAuthFormErrorMessage', () => {
  describe('ProblemDetail type takes precedence', () => {
    it('maps urn:problem-type:invalid-credentials to IT credentials message', () => {
      const err = makeAxiosError({
        status: 401,
        data: { type: 'urn:problem-type:invalid-credentials', status: 401 },
      });
      expect(getAuthFormErrorMessage(err, 'login')).toBe(
        'Email o password non validi.',
      );
    });

    it('maps urn:problem-type:email-already-registered to IT message', () => {
      const err = makeAxiosError({
        status: 409,
        data: { type: 'urn:problem-type:email-already-registered', status: 409 },
      });
      expect(getAuthFormErrorMessage(err, 'register')).toBe(
        'Email già registrata.',
      );
    });

    it('maps urn:problem-type:rate-limited to throttling message', () => {
      const err = makeAxiosError({
        status: 429,
        data: { type: 'urn:problem-type:rate-limited', status: 429 },
      });
      expect(getAuthFormErrorMessage(err, 'login')).toBe(
        'Troppe richieste. Riprova tra qualche istante.',
      );
    });
  });

  describe('falls back to HTTP status when ProblemDetail type missing', () => {
    it('401 on login → invalid credentials message', () => {
      const err = makeAxiosError({ status: 401, data: { detail: 'x' } });
      expect(getAuthFormErrorMessage(err, 'login')).toBe(
        'Email o password non validi.',
      );
    });

    it('401 on register → session not valid message', () => {
      const err = makeAxiosError({ status: 401 });
      expect(getAuthFormErrorMessage(err, 'register')).toBe(
        "Sessione non valida. Effettua nuovamente l'accesso.",
      );
    });

    it('409 on register → email already registered', () => {
      const err = makeAxiosError({ status: 409 });
      expect(getAuthFormErrorMessage(err, 'register')).toBe(
        'Email già registrata.',
      );
    });

    it('422 → validation message', () => {
      const err = makeAxiosError({ status: 422 });
      expect(getAuthFormErrorMessage(err, 'register')).toBe(
        'Dati non validi. Controlla i campi inseriti.',
      );
    });

    it('500 → generic server error', () => {
      const err = makeAxiosError({ status: 500 });
      expect(getAuthFormErrorMessage(err, 'login')).toBe(
        'Errore del server. Riprova tra qualche istante.',
      );
    });

    it('503 → generic server error', () => {
      const err = makeAxiosError({ status: 503 });
      expect(getAuthFormErrorMessage(err, 'register')).toBe(
        'Errore del server. Riprova tra qualche istante.',
      );
    });
  });

  describe('network errors (no response) → connection message', () => {
    it('AxiosError without response → connectivity message', () => {
      const err = makeAxiosError({ noResponse: true });
      expect(getAuthFormErrorMessage(err, 'login')).toBe(
        'Impossibile contattare il server. Verifica la connessione e riprova.',
      );
    });
  });

  describe('unknown errors never leak raw message', () => {
    it('non-Axios Error → generic IT fallback', () => {
      const raw = new Error('Request failed with status code 401 — internal detail');
      expect(getAuthFormErrorMessage(raw, 'login')).toBe(
        'Si è verificato un errore imprevisto. Riprova.',
      );
    });

    it('plain object thrown → generic IT fallback', () => {
      expect(getAuthFormErrorMessage({ foo: 'bar' }, 'login')).toBe(
        'Si è verificato un errore imprevisto. Riprova.',
      );
    });

    it('string thrown → generic IT fallback', () => {
      expect(getAuthFormErrorMessage('boom', 'register')).toBe(
        'Si è verificato un errore imprevisto. Riprova.',
      );
    });

    it('never returns a string containing "401"/"409" or raw err.message', () => {
      const cases: unknown[] = [
        new Error('Request failed with status code 401'),
        new Error('boom 409 conflict'),
        makeAxiosError({ status: 401 }),
        makeAxiosError({ status: 409 }),
      ];
      for (const c of cases) {
        const msg = getAuthFormErrorMessage(c, 'login');
        expect(msg).not.toContain('401');
        expect(msg).not.toContain('409');
        expect(msg).not.toContain('Request failed');
      }
    });
  });
});
