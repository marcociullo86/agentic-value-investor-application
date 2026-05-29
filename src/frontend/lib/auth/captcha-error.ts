import axios from 'axios';
import type { ProblemDetail } from '@/lib/api/network-error-interceptor';

/**
 * CAPTCHA error detection (TSK-238 — US-081 / ADR-025 §5).
 *
 * The BE signals a CAPTCHA gate via RFC 9457 ProblemDetail on a 401
 * response (see `GlobalExceptionHandler.handleCaptchaRequired`):
 *
 *   {
 *     "type":   "https://api/errors/captcha-required",
 *     "status": 401,
 *     "detail": "Invalid email or password",
 *     "captchaRequired": true
 *   }
 *
 * The `detail` is intentionally identical to a regular bad-credentials
 * response so an attacker cannot use the response text to discriminate
 * "email exists / brute-force counter tripped" — the only honest signal
 * is the `captchaRequired` extension. The FE therefore inspects the
 * extension flag (and a fallback on the problem `type`) rather than
 * the user-facing message.
 */

/** ProblemDetail `type` URI used by the BE for CAPTCHA gating. */
const CAPTCHA_PROBLEM_TYPE = 'https://api/errors/captcha-required';

/**
 * `true` iff the underlying error is an Axios 401 carrying the
 * `captchaRequired: true` ProblemDetail extension (or, as a defensive
 * fallback, the well-known captcha-required `type` URI).
 *
 * Returns `false` for non-Axios errors, missing responses, and any
 * other 4xx/5xx that lacks the extension — so callers can safely
 * pipe every catch through this helper without false positives.
 */
export function isCaptchaRequiredError(error: unknown): boolean {
  if (!axios.isAxiosError(error)) {
    return false;
  }
  if (error.response?.status !== 401) {
    return false;
  }
  const data = error.response.data;
  if (!data || typeof data !== 'object') {
    return false;
  }
  const problem = data as ProblemDetail;
  if (problem.captchaRequired === true) {
    return true;
  }
  return problem.type === CAPTCHA_PROBLEM_TYPE;
}
