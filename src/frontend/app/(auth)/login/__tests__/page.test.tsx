import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LoginPage from '../page';

const pushMock = vi.fn();
const loginMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock }),
}));

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({ login: loginMock }),
}));

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
