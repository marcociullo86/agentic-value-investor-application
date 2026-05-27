import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders } from 'axios';
import MfaEnrollmentPage from '../page';

const pushMock = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: pushMock, replace: vi.fn() }),
}));

vi.mock('@/components/auth/AuthGuard', () => ({
  AuthGuard: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}));

vi.mock('@/lib/api/auth', () => ({
  enrollMfa: vi.fn(),
  verifyMfa: vi.fn(),
  disableMfa: vi.fn(),
}));

import * as authApi from '@/lib/api/auth';

const mockedEnroll = authApi.enrollMfa as ReturnType<typeof vi.fn>;
const mockedVerify = authApi.verifyMfa as ReturnType<typeof vi.fn>;
const mockedDisable = authApi.disableMfa as ReturnType<typeof vi.fn>;

const SAMPLE_ENROLLMENT = {
  secret: 'JBSWY3DPEHPK3PXP',
  qrCodeUri: 'otpauth://totp/ValueInvesting:alice@example.com?secret=JBSWY3DPEHPK3PXP&issuer=ValueInvesting',
  recoveryCodes: [
    'AAAA-BBBB-1',
    'AAAA-BBBB-2',
    'AAAA-BBBB-3',
    'AAAA-BBBB-4',
    'AAAA-BBBB-5',
    'AAAA-BBBB-6',
    'AAAA-BBBB-7',
    'AAAA-BBBB-8',
  ],
};

function makeAxiosError(status: number): AxiosError {
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
      data: undefined,
    },
  );
}

describe('MfaEnrollmentPage — TSK-232', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('starts in intro stage and triggers enrollment on click', async () => {
    const user = userEvent.setup();
    mockedEnroll.mockResolvedValueOnce(SAMPLE_ENROLLMENT);
    render(<MfaEnrollmentPage />);

    expect(screen.getByTestId('mfa-enroll-submit')).toBeInTheDocument();

    await user.click(screen.getByTestId('mfa-enroll-submit'));

    expect(mockedEnroll).toHaveBeenCalledTimes(1);
    expect(await screen.findByTestId('mfa-otpauth-uri')).toHaveTextContent(
      'otpauth://totp/ValueInvesting',
    );
    expect(screen.getByTestId('mfa-secret')).toHaveTextContent(
      'JBSWY3DPEHPK3PXP',
    );
  });

  it('shows IT error when enrollment returns 409', async () => {
    const user = userEvent.setup();
    mockedEnroll.mockRejectedValueOnce(makeAxiosError(409));
    render(<MfaEnrollmentPage />);

    await user.click(screen.getByTestId('mfa-enroll-submit'));

    expect(await screen.findByTestId('mfa-enroll-error')).toHaveTextContent(
      'MFA è già attivo su questo account.',
    );
  });

  it('verifies TOTP and displays recovery codes', async () => {
    const user = userEvent.setup();
    mockedEnroll.mockResolvedValueOnce(SAMPLE_ENROLLMENT);
    mockedVerify.mockResolvedValueOnce(undefined);
    render(<MfaEnrollmentPage />);

    await user.click(screen.getByTestId('mfa-enroll-submit'));
    await user.type(
      await screen.findByTestId('mfa-verify-input'),
      '123456',
    );
    await user.click(screen.getByTestId('mfa-verify-submit'));

    await waitFor(() =>
      expect(mockedVerify).toHaveBeenCalledWith({ totpCode: '123456' }),
    );
    const codes = await screen.findByTestId('mfa-recovery-codes');
    expect(codes).toBeInTheDocument();
    expect(codes.querySelectorAll('li')).toHaveLength(8);
  });

  it('rejects malformed TOTP code (less than 6 digits) with inline error', async () => {
    const user = userEvent.setup();
    mockedEnroll.mockResolvedValueOnce(SAMPLE_ENROLLMENT);
    render(<MfaEnrollmentPage />);

    await user.click(screen.getByTestId('mfa-enroll-submit'));
    await user.type(await screen.findByTestId('mfa-verify-input'), '12');
    await user.click(screen.getByTestId('mfa-verify-submit'));

    expect(
      await screen.findByText('Il codice deve essere di 6 cifre'),
    ).toBeInTheDocument();
    expect(mockedVerify).not.toHaveBeenCalled();
  });

  it('shows IT error when verify returns 400', async () => {
    const user = userEvent.setup();
    mockedEnroll.mockResolvedValueOnce(SAMPLE_ENROLLMENT);
    mockedVerify.mockRejectedValueOnce(makeAxiosError(400));
    render(<MfaEnrollmentPage />);

    await user.click(screen.getByTestId('mfa-enroll-submit'));
    await user.type(
      await screen.findByTestId('mfa-verify-input'),
      '000000',
    );
    await user.click(screen.getByTestId('mfa-verify-submit'));

    expect(await screen.findByTestId('mfa-verify-error')).toHaveTextContent(
      'Codice TOTP non valido',
    );
  });

  it('confirms recovery codes acknowledgement and redirects to /', async () => {
    const user = userEvent.setup();
    mockedEnroll.mockResolvedValueOnce(SAMPLE_ENROLLMENT);
    mockedVerify.mockResolvedValueOnce(undefined);
    render(<MfaEnrollmentPage />);

    await user.click(screen.getByTestId('mfa-enroll-submit'));
    await user.type(
      await screen.findByTestId('mfa-verify-input'),
      '123456',
    );
    await user.click(screen.getByTestId('mfa-verify-submit'));

    const confirmButton = await screen.findByTestId('mfa-codes-confirm');
    expect(confirmButton).toBeDisabled();

    await user.click(screen.getByTestId('mfa-codes-ack'));
    expect(confirmButton).toBeEnabled();

    await user.click(confirmButton);
    expect(pushMock).toHaveBeenCalledWith('/');
  });

  it('disables MFA after password confirmation', async () => {
    const user = userEvent.setup();
    mockedDisable.mockResolvedValueOnce(undefined);
    render(<MfaEnrollmentPage />);

    await user.type(
      screen.getByTestId('mfa-disable-password'),
      'super-secret-pw',
    );
    await user.click(screen.getByTestId('mfa-disable-submit'));

    await waitFor(() =>
      expect(mockedDisable).toHaveBeenCalledWith({ password: 'super-secret-pw' }),
    );
    expect(await screen.findByTestId('mfa-disable-success')).toHaveTextContent(
      'MFA disattivato.',
    );
  });

  it('shows IT error when disable returns 401 (wrong password)', async () => {
    const user = userEvent.setup();
    mockedDisable.mockRejectedValueOnce(makeAxiosError(401));
    render(<MfaEnrollmentPage />);

    await user.type(screen.getByTestId('mfa-disable-password'), 'wrong-pw');
    await user.click(screen.getByTestId('mfa-disable-submit'));

    expect(await screen.findByTestId('mfa-disable-error')).toHaveTextContent(
      'Password non corretta.',
    );
  });
});
