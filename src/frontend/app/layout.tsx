import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { AuthProvider } from '@/components/providers/AuthProvider';
import { ToastProvider } from '@/components/providers/ToastProvider';
import { Navbar } from '@/components/layout/Navbar';
import { SessionExpiredBanner } from '@/components/auth/SessionExpiredBanner';
import './globals.css';

export const metadata: Metadata = {
  title: 'Value Investing WebApp',
  description:
    'Analisi quantitative Graham/Buffett su titoli quotati USA + watchlist personale.',
};

/**
 * Root layout (Next.js 16 App Router).
 *
 * Riferimento: design_&_architecture/components/frontend-components.md
 *   §app/layout.tsx — wrappa AuthProvider e Toaster.
 *
 * `lang="it"` per assistive technology (US-014 AC accessibilità).
 */
export default function RootLayout({
  children,
}: {
  readonly children: ReactNode;
}) {
  return (
    <html lang="it" suppressHydrationWarning>
      <body>
        <AuthProvider>
          <ToastProvider>
            <SessionExpiredBanner />
            <Navbar />
            {children}
          </ToastProvider>
        </AuthProvider>
      </body>
    </html>
  );
}
