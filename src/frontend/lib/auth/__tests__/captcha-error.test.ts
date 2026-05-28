import { describe, it, expect } from 'vitest';
import { AxiosError, AxiosHeaders } from 'axios';
import { isCaptchaRequiredError } from '../captcha-error';

/**
 * Coverage matrix:
 *  - non-Axios → false
 *  - Axios without response → false
 *  - Axios 401 with `captchaRequired: true` → true
 *  - Axios 401 with the captcha-required `type` URI but no extension → true
 *  - Axios 401 plain bad-credentials → false (no false positives)
 *  - Axios 423 (account-locked) → false
 *  - Axios 401 with non-object body → false
 */
describe('isCaptchaRequiredError (TSK-238)', () => {
  function makeAxiosError(status: number, data: unknown): AxiosError {
    const error = new AxiosError(
      `Request failed with status code ${status}`,
      'ERR_BAD_REQUEST',
    );
    error.response = {
      status,
      statusText: '',
      headers: new AxiosHeaders(),
      data,
      config: { headers: new AxiosHeaders() } as never,
    };
    return error;
  }

  it('returns false for non-Axios errors', () => {
    expect(isCaptchaRequiredError(new Error('boom'))).toBe(false);
    expect(isCaptchaRequiredError('string error')).toBe(false);
    expect(isCaptchaRequiredError(null)).toBe(false);
    expect(isCaptchaRequiredError(undefined)).toBe(false);
  });

  it('returns false for Axios errors without a response', () => {
    const error = new AxiosError('Network Error', 'ERR_NETWORK');
    expect(isCaptchaRequiredError(error)).toBe(false);
  });

  it('returns true on 401 + captchaRequired=true extension', () => {
    const error = makeAxiosError(401, {
      type: 'https://api/errors/captcha-required',
      title: 'Captcha required',
      status: 401,
      detail: 'Invalid email or password',
      captchaRequired: true,
    });
    expect(isCaptchaRequiredError(error)).toBe(true);
  });

  it('returns true on 401 + captcha-required `type` URI even without extension', () => {
    const error = makeAxiosError(401, {
      type: 'https://api/errors/captcha-required',
      status: 401,
      detail: 'Invalid email or password',
    });
    expect(isCaptchaRequiredError(error)).toBe(true);
  });

  it('returns false on 401 plain bad-credentials (no extension, no captcha type)', () => {
    const error = makeAxiosError(401, {
      type: 'https://api/errors/invalid-credentials',
      status: 401,
      detail: 'Invalid email or password',
    });
    expect(isCaptchaRequiredError(error)).toBe(false);
  });

  it('returns false on 423 account-locked (different gate, same family)', () => {
    const error = makeAxiosError(423, {
      type: 'https://api/errors/account-locked',
      status: 423,
      detail: 'Account temporarily locked due to repeated failed login attempts',
      retryAfterSeconds: 1800,
    });
    expect(isCaptchaRequiredError(error)).toBe(false);
  });

  it('returns false when the response body is not an object', () => {
    const error = makeAxiosError(401, 'plain text');
    expect(isCaptchaRequiredError(error)).toBe(false);
  });

  it('returns false when captchaRequired is the wrong type', () => {
    const error = makeAxiosError(401, {
      type: 'https://api/errors/something-else',
      captchaRequired: 'true', // string, not boolean
    });
    expect(isCaptchaRequiredError(error)).toBe(false);
  });
});
