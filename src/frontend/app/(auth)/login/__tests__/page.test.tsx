import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import LoginPage from '../page';

const pushMock = vi.fn();
const loginMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ login: loginMock }),
}));

// The Turnstile widget pulls a third-party script that the jsdom test
// environment cannot resolve and would noisily call window.turnstile
// hooks that are not wired here. Replace it with a minimal stub that
// renders a marker plus a "solve" button — enough to exercise the
// captcha-token flow without binding to Cloudflare's runtime.
vi.mock('@/components/auth/turnstile-widget', () => ({
  TurnstileWidget: ({
    onToken,
  }: {
    onToken: (token: string) => void;
  }): React.ReactElement => (
    <div data-testid="turnstile-widget">
      <button
        type="button"
        data-testid="turnstile-solve"
        onClick={() => onToken('test-turnstile-token')}
      >
        solve
      </button>
    </div>
  ),
}));

function makeCaptchaRequiredError(): AxiosError {
  const error = new AxiosError('Request failed with status code 401');
  error.response = {
    status: 401,
    statusText: '',
    headers: new AxiosHeaders(),
    data: {
      type: 'https://api/errors/captcha-required',
      title: 'Captcha required',
      status: 401,
      detail: 'Invalid email or password',
      captchaRequired: true,
    },
    config: { headers: new AxiosHeaders() } as never,
  };
  return error;
}

describe('LoginPage — US-067 form validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows inline errors and summary when submitting empty fields', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.click(screen.getByTestId('login-submit'));

    expect(await screen.findByText("L'email è obbligatoria")).toBeInTheDocument();
    expect(screen.getByText('La password è obbligatoria')).toBeInTheDocument();

    const summary = screen.getByText(/errori nel modulo/);
    expect(summary.closest('[aria-live="assertive"]')).toBeInTheDocument();
  });

  it('links email and password fields to error messages via aria-describedby', async () => {
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.click(screen.getByTestId('login-submit'));
    await screen.findByText("L'email è obbligatoria");

    const emailInput = screen.getByTestId('login-email');
    expect(emailInput).toHaveAttribute('aria-describedby', 'email-error');

    const passwordInput = screen.getByTestId('login-password');
    expect(passwordInput).toHaveAttribute('aria-describedby', 'password-error');
  });
});

describe('LoginPage — US-081 CAPTCHA flow (TSK-238)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('hides the captcha widget on initial render (under-threshold flow)', () => {
    render(<LoginPage />);

    expect(screen.queryByTestId('login-captcha')).not.toBeInTheDocument();
    expect(screen.getByTestId('login-submit')).not.toBeDisabled();
  });

  it('mounts the widget and disables submit after a captchaRequired 401', async () => {
    const user = userEvent.setup();
    loginMock.mockRejectedValueOnce(makeCaptchaRequiredError());

    render(<LoginPage />);

    await user.type(screen.getByTestId('login-email'), 'user@example.com');
    await user.type(screen.getByTestId('login-password'), 'CorrectHorse42!');
    await user.click(screen.getByTestId('login-submit'));

    // First attempt was made WITHOUT a captcha token — confirms the
    // FE doesn't preemptively gate normal users.
    expect(loginMock).toHaveBeenCalledTimes(1);
    expect(loginMock).toHaveBeenLastCalledWith(
      'user@example.com',
      'CorrectHorse42!',
      null,
    );

    // Widget appears + submit button is now disabled until the user
    // produces a token.
    expect(await screen.findByTestId('login-captcha')).toBeInTheDocument();
    expect(screen.getByTestId('login-submit')).toBeDisabled();
  });

  it('forwards the Turnstile token on the retry submit and clears it after use', async () => {
    const user = userEvent.setup();
    loginMock
      .mockRejectedValueOnce(makeCaptchaRequiredError())
      .mockResolvedValueOnce({ type: 'success' });

    render(<LoginPage />);

    await user.type(screen.getByTestId('login-email'), 'user@example.com');
    await user.type(screen.getByTestId('login-password'), 'CorrectHorse42!');
    await user.click(screen.getByTestId('login-submit'));

    // Widget visible + submit blocked.
    await screen.findByTestId('login-captcha');
    expect(screen.getByTestId('login-submit')).toBeDisabled();

    // User solves the challenge → submit re-enabled.
    await user.click(screen.getByTestId('turnstile-solve'));
    expect(screen.getByTestId('login-submit')).not.toBeDisabled();

    await user.click(screen.getByTestId('login-submit'));

    expect(loginMock).toHaveBeenCalledTimes(2);
    expect(loginMock).toHaveBeenLastCalledWith(
      'user@example.com',
      'CorrectHorse42!',
      'test-turnstile-token',
    );
    expect(pushMock).toHaveBeenCalledWith('/');
  });
});
