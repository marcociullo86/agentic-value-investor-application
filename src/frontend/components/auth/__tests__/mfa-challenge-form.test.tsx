import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import { MfaChallengeForm } from '../mfa-challenge-form';

function makeAxiosError(status: number, problemType?: string): AxiosError {
  const headers = new AxiosHeaders();
  const config = { headers };
  return new AxiosError(
    'Request failed',
    'ERR_BAD_REQUEST',
    config as never,
    undefined,
    {
      status,
      statusText: 'X',
      headers: {},
      config: config as never,
      data: problemType ? { type: problemType, status } : undefined,
    },
  );
}

const completeChallengeMock = vi.fn();
const completeRecoveryMock = vi.fn();
const onSuccessMock = vi.fn();

vi.mock('@/lib/stores/useAuthStore', () => ({
  useAuthStore: (selector: (s: Record<string, unknown>) => unknown) =>
    selector({
      completeMfaChallenge: completeChallengeMock,
      completeMfaRecovery: completeRecoveryMock,
    }),
}));

function renderForm(): void {
  render(
    <MfaChallengeForm
      mfaToken="mfa-token-xyz"
      email="alice@example.com"
      onSuccess={onSuccessMock}
    />,
  );
}

describe('MfaChallengeForm — TSK-233', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('rejects non-numeric or wrong-length TOTP codes with inline error', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.type(screen.getByTestId('mfa-totp-input'), '12');
    await user.click(screen.getByTestId('mfa-totp-submit'));

    expect(
      await screen.findByText('Il codice deve essere di 6 cifre'),
    ).toBeInTheDocument();
    expect(completeChallengeMock).not.toHaveBeenCalled();
  });

  it('submits the TOTP challenge and calls onSuccess', async () => {
    const user = userEvent.setup();
    completeChallengeMock.mockResolvedValueOnce(undefined);
    renderForm();

    await user.type(screen.getByTestId('mfa-totp-input'), '123456');
    await user.click(screen.getByTestId('mfa-totp-submit'));

    expect(completeChallengeMock).toHaveBeenCalledWith(
      'mfa-token-xyz',
      '123456',
      'alice@example.com',
    );
    await screen.findByTestId('mfa-totp-input');
    expect(onSuccessMock).toHaveBeenCalledTimes(1);
  });

  it('shows a user-safe error message on TOTP failure (Axios 400)', async () => {
    const user = userEvent.setup();
    completeChallengeMock.mockRejectedValueOnce(
      makeAxiosError(400, 'urn:problem-type:invalid-credentials'),
    );
    renderForm();

    await user.type(screen.getByTestId('mfa-totp-input'), '000000');
    await user.click(screen.getByTestId('mfa-totp-submit'));

    expect(await screen.findByTestId('mfa-challenge-error')).toBeInTheDocument();
    expect(onSuccessMock).not.toHaveBeenCalled();
  });

  it('switches to recovery mode and submits recovery code', async () => {
    const user = userEvent.setup();
    completeRecoveryMock.mockResolvedValueOnce(undefined);
    renderForm();

    await user.click(screen.getByTestId('mfa-use-recovery'));
    expect(screen.getByTestId('mfa-recovery-input')).toBeInTheDocument();

    await user.type(screen.getByTestId('mfa-recovery-input'), 'ABCD-EFGH-IJKL');
    await user.click(screen.getByTestId('mfa-recovery-submit'));

    expect(completeRecoveryMock).toHaveBeenCalledWith(
      'mfa-token-xyz',
      'ABCD-EFGH-IJKL',
      'alice@example.com',
    );
    expect(onSuccessMock).toHaveBeenCalledTimes(1);
  });

  it('TOTP input has accessible label and aria-describedby on error', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByTestId('mfa-totp-submit'));
    await screen.findByText('Inserisci il codice a 6 cifre');

    const input = screen.getByTestId('mfa-totp-input');
    expect(input).toHaveAttribute('aria-describedby', 'totpCode-error');
    expect(input).toHaveAttribute('autocomplete', 'one-time-code');
  });

  it('switches back to TOTP from recovery via "Usa il codice TOTP"', async () => {
    const user = userEvent.setup();
    renderForm();

    await user.click(screen.getByTestId('mfa-use-recovery'));
    await user.click(screen.getByTestId('mfa-use-totp'));

    expect(screen.getByTestId('mfa-totp-input')).toBeInTheDocument();
    expect(screen.queryByTestId('mfa-recovery-input')).not.toBeInTheDocument();
  });
});
