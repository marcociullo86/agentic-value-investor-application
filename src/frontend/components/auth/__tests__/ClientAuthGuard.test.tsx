import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

/**
 * Integration tests for `ClientAuthGuard` + backward-compat `AuthGuard`
 * (TSK-266 / US-087). Mocks the hook to verify the rendering contract:
 * children only when allowed, fallback otherwise.
 */

const mockDecision: { current: { type: string } } = { current: { type: 'allow' } };

vi.mock('@/hooks/use-auth-guard', () => ({
  useAuthGuard: () => mockDecision.current,
}));

import { ClientAuthGuard } from '../ClientAuthGuard';
import { AuthGuard } from '../AuthGuard';

describe('ClientAuthGuard', () => {
  beforeEach(() => {
    mockDecision.current = { type: 'allow' };
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders children when decision is allow', () => {
    mockDecision.current = { type: 'allow' };
    render(
      <ClientAuthGuard fallback={<span>FALLBACK</span>}>
        <span>PROTECTED</span>
      </ClientAuthGuard>,
    );
    expect(screen.getByText('PROTECTED')).toBeInTheDocument();
    expect(screen.queryByText('FALLBACK')).not.toBeInTheDocument();
  });

  it.each(['loading', 'unauthenticated', 'forbidden', 'session-expired'] as const)(
    'renders fallback when decision is %s',
    (type) => {
      mockDecision.current = { type };
      render(
        <ClientAuthGuard fallback={<span>FALLBACK</span>}>
          <span>PROTECTED</span>
        </ClientAuthGuard>,
      );
      expect(screen.getByText('FALLBACK')).toBeInTheDocument();
      expect(screen.queryByText('PROTECTED')).not.toBeInTheDocument();
    },
  );

  it('renders null fallback by default (no flicker placeholder)', () => {
    mockDecision.current = { type: 'unauthenticated' };
    const { container } = render(
      <ClientAuthGuard>
        <span>PROTECTED</span>
      </ClientAuthGuard>,
    );
    expect(container.textContent).toBe('');
  });

  describe('AuthGuard alias — backward compatibility', () => {
    it('delegates to ClientAuthGuard and renders children when allowed', () => {
      mockDecision.current = { type: 'allow' };
      render(
        <AuthGuard fallback={<span>FALLBACK</span>}>
          <span>PROTECTED</span>
        </AuthGuard>,
      );
      expect(screen.getByText('PROTECTED')).toBeInTheDocument();
    });

    it('delegates to ClientAuthGuard and renders fallback when blocked', () => {
      mockDecision.current = { type: 'unauthenticated' };
      render(
        <AuthGuard fallback={<span>FALLBACK</span>}>
          <span>PROTECTED</span>
        </AuthGuard>,
      );
      expect(screen.getByText('FALLBACK')).toBeInTheDocument();
    });
  });
});
