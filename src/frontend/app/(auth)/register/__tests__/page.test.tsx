import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
