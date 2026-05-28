import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage, { resolveSafeReturnUrl } from '../page';

const pushMock = vi.fn();
const loginMock = vi.fn();
let searchParamsMock = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
  useSearchParams: () => searchParamsMock,
}));

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ login: loginMock }),
}));

describe('LoginPage — US-067 form validation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    searchParamsMock = new URLSearchParams();
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

describe('LoginPage — TSK-267 returnUrl post-login redirect', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    searchParamsMock = new URLSearchParams();
  });

  it('redirects to "/" by default when no returnUrl is present', async () => {
    loginMock.mockResolvedValueOnce({ type: 'success' });
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByTestId('login-email'), 'a@b.io');
    await user.type(screen.getByTestId('login-password'), 'CorrectPass-123!');
    await user.click(screen.getByTestId('login-submit'));

    await vi.waitFor(() => expect(pushMock).toHaveBeenCalledTimes(1));
    expect(pushMock).toHaveBeenCalledWith('/');
  });

  it('redirects to the returnUrl (path + query) on successful login', async () => {
    loginMock.mockResolvedValueOnce({ type: 'success' });
    searchParamsMock = new URLSearchParams('returnUrl=/analysis?ticker=AAPL');
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByTestId('login-email'), 'a@b.io');
    await user.type(screen.getByTestId('login-password'), 'CorrectPass-123!');
    await user.click(screen.getByTestId('login-submit'));

    await vi.waitFor(() => expect(pushMock).toHaveBeenCalledTimes(1));
    expect(pushMock).toHaveBeenCalledWith('/analysis?ticker=AAPL');
  });

  it('ignores a tampered returnUrl pointing to an external origin', async () => {
    loginMock.mockResolvedValueOnce({ type: 'success' });
    searchParamsMock = new URLSearchParams('returnUrl=//evil.example.com/steal');
    const user = userEvent.setup();
    render(<LoginPage />);

    await user.type(screen.getByTestId('login-email'), 'a@b.io');
    await user.type(screen.getByTestId('login-password'), 'CorrectPass-123!');
    await user.click(screen.getByTestId('login-submit'));

    await vi.waitFor(() => expect(pushMock).toHaveBeenCalledTimes(1));
    expect(pushMock).toHaveBeenCalledWith('/');
  });

  it('renders the session-expired banner when ?expired=true', () => {
    searchParamsMock = new URLSearchParams('expired=true&returnUrl=/watchlist');
    render(<LoginPage />);
    expect(screen.getByTestId('session-expired-alert')).toBeInTheDocument();
  });
});

describe('resolveSafeReturnUrl — open-redirect defence', () => {
  it.each([
    [null, '/'],
    ['', '/'],
    ['/', '/'],
    ['/watchlist', '/watchlist'],
    ['/analysis?ticker=AAPL', '/analysis?ticker=AAPL'],
    ['/top-picks?sector=Tech&min_mos=20', '/top-picks?sector=Tech&min_mos=20'],
  ])('accepts safe same-origin path %s -> %s', (raw, expected) => {
    expect(resolveSafeReturnUrl(raw)).toBe(expected);
  });

  it.each([
    ['//evil.example.com/steal'],
    ['https://evil.example.com'],
    ['http://evil.example.com'],
    ['evil.example.com'],
    ['javascript:alert(1)'],
    ['/login'],
    ['/login?expired=true'],
    ['/login/sub'],
  ])('rejects unsafe returnUrl %s', (raw) => {
    expect(resolveSafeReturnUrl(raw)).toBe('/');
  });
});
