import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import * as authApi from '@/lib/api/auth';
import RegisterPage from '../page';

const pushMock = vi.fn();
const loginMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ login: loginMock }),
}));

vi.mock('@/lib/api/auth', () => ({
  register: vi.fn(),
}));

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

describe('RegisterPage — US-067 form validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows confirmPassword mismatch error when passwords differ', async () => {
    const user = userEvent.setup();
    render(<RegisterPage />);

    await user.type(screen.getByTestId('register-email'), 'test@example.com');
    await user.type(screen.getByTestId('register-password'), 'Password1234!');
    await user.type(screen.getByTestId('register-confirm-password'), 'DifferentPass1!');

    await user.click(screen.getByTestId('register-submit'));

    expect(
      await screen.findByText('Le password non coincidono'),
    ).toBeInTheDocument();

    const confirmInput = screen.getByTestId('register-confirm-password');
    expect(confirmInput).toHaveAttribute('aria-describedby', 'confirmPassword-error');
  });

  it('renders error summary with field label for confirmPassword', async () => {
    const user = userEvent.setup();
    render(<RegisterPage />);

    await user.type(screen.getByTestId('register-email'), 'test@example.com');
    await user.type(screen.getByTestId('register-password'), 'Password1234!');
    await user.type(screen.getByTestId('register-confirm-password'), 'DifferentPass1!');

    await user.click(screen.getByTestId('register-submit'));

    const summary = await screen.findByText(/errore nel modulo/);
    expect(summary.closest('[aria-live="assertive"]')).toBeInTheDocument();
    expect(summary.closest('[aria-live]')!.textContent).toContain(
      'Conferma password',
    );
  });
});

describe('RegisterPage — US-081 CAPTCHA flow (TSK-238)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  async function fillForm(user: ReturnType<typeof userEvent.setup>): Promise<void> {
    await user.type(screen.getByTestId('register-email'), 'newuser@example.com');
    await user.type(screen.getByTestId('register-password'), 'Password1234!');
    await user.type(
      screen.getByTestId('register-confirm-password'),
      'Password1234!',
    );
  }

  it('does not show the captcha widget on initial render', () => {
    render(<RegisterPage />);

    expect(screen.queryByTestId('register-captcha')).not.toBeInTheDocument();
    expect(screen.getByTestId('register-submit')).not.toBeDisabled();
  });

  it('mounts the widget and disables submit after a captchaRequired 401 from /register', async () => {
    const user = userEvent.setup();
    const registerMock = vi.mocked(authApi.register);
    registerMock.mockRejectedValueOnce(makeCaptchaRequiredError());

    render(<RegisterPage />);
    await fillForm(user);
    await user.click(screen.getByTestId('register-submit'));

    expect(registerMock).toHaveBeenCalledTimes(1);
    expect(registerMock).toHaveBeenLastCalledWith(
      expect.objectContaining({
        email: 'newuser@example.com',
        password: 'Password1234!',
        captchaToken: null,
      }),
    );
    expect(loginMock).not.toHaveBeenCalled();

    expect(await screen.findByTestId('register-captcha')).toBeInTheDocument();
    expect(screen.getByTestId('register-submit')).toBeDisabled();
  });

  it('forwards the Turnstile token on the retry submit', async () => {
    const user = userEvent.setup();
    const registerMock = vi.mocked(authApi.register);
    registerMock
      .mockRejectedValueOnce(makeCaptchaRequiredError())
      .mockResolvedValueOnce({
        id: 'mock-id',
        email: 'newuser@example.com',
        displayName: null,
        createdAt: '2026-05-28T22:00:00Z',
      });
    loginMock.mockResolvedValueOnce({ type: 'success' });

    render(<RegisterPage />);
    await fillForm(user);
    await user.click(screen.getByTestId('register-submit'));

    await screen.findByTestId('register-captcha');
    expect(screen.getByTestId('register-submit')).toBeDisabled();

    await user.click(screen.getByTestId('turnstile-solve'));
    expect(screen.getByTestId('register-submit')).not.toBeDisabled();

    await user.click(screen.getByTestId('register-submit'));

    expect(registerMock).toHaveBeenCalledTimes(2);
    expect(registerMock).toHaveBeenLastCalledWith(
      expect.objectContaining({
        email: 'newuser@example.com',
        password: 'Password1234!',
        captchaToken: 'test-turnstile-token',
      }),
    );
    // Auto-login with no captchaToken — single-use ensures we don't
    // replay the consumed token.
    expect(loginMock).toHaveBeenCalledWith(
      'newuser@example.com',
      'Password1234!',
    );
    expect(pushMock).toHaveBeenCalledWith('/');
  });
});
