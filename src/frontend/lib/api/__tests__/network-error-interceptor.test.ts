import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  interceptFetch,
  createFetcher,
  NetworkError,
} from '../network-error-interceptor';
import locale from '@/locales/it.json';

type NotifyMock = {
  success: ReturnType<typeof vi.fn>;
  info: ReturnType<typeof vi.fn>;
  warning: ReturnType<typeof vi.fn>;
  error: ReturnType<typeof vi.fn>;
};

function createNotifyMock(): NotifyMock {
  return {
    success: vi.fn(() => 'mock-id'),
    info: vi.fn(() => 'mock-id'),
    warning: vi.fn(() => 'mock-id'),
    error: vi.fn(() => 'mock-id'),
  };
}

function jsonResponse(
  status: number,
  body: unknown,
  headers?: Record<string, string>,
): Response {
  const h = new Headers({ 'Content-Type': 'application/json', ...headers });
  return new Response(JSON.stringify(body), { status, headers: h });
}

describe('interceptFetch — offline (AC: navigator.onLine false)', () => {
  let notify: NotifyMock;
  const originalOnLine = navigator.onLine;

  beforeEach(() => {
    notify = createNotifyMock();
    Object.defineProperty(navigator, 'onLine', {
      value: false,
      writable: true,
      configurable: true,
    });
  });

  afterEach(() => {
    Object.defineProperty(navigator, 'onLine', {
      value: originalOnLine,
      writable: true,
      configurable: true,
    });
  });

  it('throws NetworkError with category "offline"', async () => {
    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow(NetworkError);

    try {
      await interceptFetch('/api/test', undefined, { notify });
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne.category).toBe('offline');
    }
  });

  it('calls notify.error with offline i18n strings', async () => {
    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    expect(notify.error).toHaveBeenCalledOnce();
    expect(notify.error).toHaveBeenCalledWith(
      expect.objectContaining({
        title: locale.errors.offline.title,
        message: locale.errors.offline.message,
      }),
    );
  });

  it('does not call fetch when offline', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    expect(fetchSpy).not.toHaveBeenCalled();
    fetchSpy.mockRestore();
  });
});

describe('interceptFetch — timeout / AbortError (AC: AbortError → warning)', () => {
  let notify: NotifyMock;

  beforeEach(() => {
    notify = createNotifyMock();
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      writable: true,
      configurable: true,
    });
  });

  it('throws NetworkError with category "timeout" on AbortError', async () => {
    const abortErr = new DOMException('The operation was aborted', 'AbortError');
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(abortErr);

    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow(NetworkError);

    try {
      vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(
        new DOMException('The operation was aborted', 'AbortError'),
      );
      await interceptFetch('/api/test', undefined, { notify });
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne.category).toBe('timeout');
    }

    vi.restoreAllMocks();
  });

  it('calls notify.warning with timeout i18n strings', async () => {
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(
      new DOMException('The operation was aborted', 'AbortError'),
    );

    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    expect(notify.warning).toHaveBeenCalledOnce();
    expect(notify.warning).toHaveBeenCalledWith(
      expect.objectContaining({
        title: locale.errors.timeout.title,
        message: locale.errors.timeout.message,
      }),
    );

    vi.restoreAllMocks();
  });

  it('preserves original AbortError as cause', async () => {
    const abortErr = new DOMException('The operation was aborted', 'AbortError');
    vi.spyOn(globalThis, 'fetch').mockRejectedValueOnce(abortErr);

    try {
      await interceptFetch('/api/test', undefined, { notify });
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne.cause).toBe(abortErr);
    }

    vi.restoreAllMocks();
  });
});

describe('interceptFetch — server 500 + X-Correlation-Id (AC: correlationId propagato)', () => {
  let notify: NotifyMock;
  const CORRELATION_ID = 'test-corr-abc';
  const PROBLEM_DETAIL = {
    type: 'urn:problem-type:server-error',
    title: 'Internal Server Error',
    status: 500,
  };

  beforeEach(() => {
    notify = createNotifyMock();
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      writable: true,
      configurable: true,
    });
  });

  it('throws NetworkError with category "server", status 500, and correlationId', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(500, PROBLEM_DETAIL, {
        'X-Correlation-Id': CORRELATION_ID,
      }),
    );

    try {
      await interceptFetch('/api/test', undefined, { notify });
      expect.unreachable('should have thrown');
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne).toBeInstanceOf(NetworkError);
      expect(ne.category).toBe('server');
      expect(ne.status).toBe(500);
      expect(ne.correlationId).toBe(CORRELATION_ID);
    }

    vi.restoreAllMocks();
  });

  it('parses ProblemDetail body from JSON response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(500, PROBLEM_DETAIL, {
        'X-Correlation-Id': CORRELATION_ID,
      }),
    );

    try {
      await interceptFetch('/api/test', undefined, { notify });
      expect.unreachable('should have thrown');
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne.problemDetail).toEqual(PROBLEM_DETAIL);
    }

    vi.restoreAllMocks();
  });

  it('calls notify.error with correlationId and user-friendly message', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(500, PROBLEM_DETAIL, {
        'X-Correlation-Id': CORRELATION_ID,
      }),
    );

    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    expect(notify.error).toHaveBeenCalledOnce();
    const call = notify.error.mock.calls[0]![0] as Record<string, unknown>;
    expect(call['correlationId']).toBe(CORRELATION_ID);
    expect(call['autoDismiss']).toBe(false);
    expect(call['title']).toBe(locale.errors.serverError.title);
    expect(call['message']).toBe(locale.errors.serverError.message);

    vi.restoreAllMocks();
  });

  it('includes "Copia ID" action when correlationId is present', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(500, PROBLEM_DETAIL, {
        'X-Correlation-Id': CORRELATION_ID,
      }),
    );

    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    const call = notify.error.mock.calls[0]![0] as Record<string, unknown>;
    const actions = call['actions'] as Array<{ label: string }>;
    expect(actions).toBeDefined();
    expect(actions).toHaveLength(1);
    expect(actions[0]!.label).toBe('Copia ID');

    vi.restoreAllMocks();
  });

  it('fallback message includes correlationId for unmapped ProblemDetail type', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(
        503,
        { type: 'urn:problem-type:unknown-xyz', title: 'Unknown', status: 503 },
        { 'X-Correlation-Id': 'corr-fallback-123' },
      ),
    );

    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    const call = notify.error.mock.calls[0]![0] as Record<string, unknown>;
    expect(call['message']).toContain('corr-fallback-123');

    vi.restoreAllMocks();
  });
});

describe('interceptFetch — validation 422 (AC: messaggio user-friendly)', () => {
  let notify: NotifyMock;
  const PROBLEM_DETAIL_422 = {
    type: 'urn:problem-type:validation-failed',
    title: 'Validation Failed',
    status: 422,
    detail: 'Field "email" is invalid',
  };

  beforeEach(() => {
    notify = createNotifyMock();
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      writable: true,
      configurable: true,
    });
  });

  it('throws NetworkError with category "validation" and status 422', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(422, PROBLEM_DETAIL_422),
    );

    try {
      await interceptFetch('/api/test', undefined, { notify });
      expect.unreachable('should have thrown');
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne).toBeInstanceOf(NetworkError);
      expect(ne.category).toBe('validation');
      expect(ne.status).toBe(422);
    }

    vi.restoreAllMocks();
  });

  it('calls notify.warning with user-friendly i18n message, not raw HTTP', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(422, PROBLEM_DETAIL_422),
    );

    await expect(
      interceptFetch('/api/test', undefined, { notify }),
    ).rejects.toThrow();

    expect(notify.warning).toHaveBeenCalledOnce();
    const call = notify.warning.mock.calls[0]![0] as Record<string, unknown>;
    expect(call['title']).toBe(locale.errors.validationFailed.title);
    expect(call['message']).toBe(locale.errors.validationFailed.message);
    expect(call['title']).not.toContain('422');
    expect(call['message']).not.toContain('422');

    vi.restoreAllMocks();
  });

  it('attaches ProblemDetail body on 422', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(422, PROBLEM_DETAIL_422),
    );

    try {
      await interceptFetch('/api/test', undefined, { notify });
      expect.unreachable('should have thrown');
    } catch (err) {
      const ne = err as NetworkError;
      expect(ne.problemDetail).toEqual(PROBLEM_DETAIL_422);
    }

    vi.restoreAllMocks();
  });
});

describe('createFetcher — SWR integration (AC: factory pattern)', () => {
  let notify: NotifyMock;

  beforeEach(() => {
    notify = createNotifyMock();
    Object.defineProperty(navigator, 'onLine', {
      value: true,
      writable: true,
      configurable: true,
    });
  });

  it('returns a function', () => {
    const fetcher = createFetcher(notify);
    expect(typeof fetcher).toBe('function');
  });

  it('resolves with parsed JSON on successful response', async () => {
    const payload = { data: [1, 2, 3] };
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(200, payload),
    );

    const fetcher = createFetcher(notify);
    const result = await fetcher('/api/data');

    expect(result).toEqual(payload);
    expect(notify.error).not.toHaveBeenCalled();
    expect(notify.warning).not.toHaveBeenCalled();

    vi.restoreAllMocks();
  });

  it('throws NetworkError on 500 response', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(500, {
        type: 'urn:problem-type:server-error',
        title: 'Server Error',
        status: 500,
      }),
    );

    const fetcher = createFetcher(notify);

    try {
      await fetcher('/api/data');
      expect.unreachable('should have thrown');
    } catch (err) {
      expect(err).toBeInstanceOf(NetworkError);
      const ne = err as NetworkError;
      expect(ne.category).toBe('server');
      expect(ne.status).toBe(500);
    }

    vi.restoreAllMocks();
  });

  it('propagates notification from interceptFetch', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(
      jsonResponse(500, {
        type: 'urn:problem-type:server-error',
        title: 'Server Error',
        status: 500,
      }),
    );

    const fetcher = createFetcher(notify);
    await expect(fetcher('/api/data')).rejects.toThrow();

    expect(notify.error).toHaveBeenCalledOnce();

    vi.restoreAllMocks();
  });
});

describe('NetworkError — class shape', () => {
  it('has name "NetworkError"', () => {
    const err = new NetworkError('offline');
    expect(err.name).toBe('NetworkError');
    expect(err).toBeInstanceOf(Error);
    expect(err).toBeInstanceOf(NetworkError);
  });

  it('stores all constructor options', () => {
    const pd = { type: 'x', title: 'y', status: 503 };
    const err = new NetworkError('server', {
      status: 503,
      correlationId: 'cid',
      problemDetail: pd,
    });

    expect(err.category).toBe('server');
    expect(err.status).toBe(503);
    expect(err.correlationId).toBe('cid');
    expect(err.problemDetail).toBe(pd);
    expect(err.message).toBe('Network error: server');
  });
});
