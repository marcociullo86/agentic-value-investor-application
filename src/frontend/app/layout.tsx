import type { Metadata } from 'next';
import type { ReactNode } from 'react';
import { AuthProvider } from '@/components/providers/AuthProvider';
import { ToastProvider } from '@/components/providers/ToastProvider';
import { NotificationProvider } from '@/components/notifications/notification-provider';
import { NotificationContainer } from '@/components/notifications/notification-container';
import { ThemeProvider } from '@/components/theme/theme-provider';
import { Navbar } from '@/components/layout/Navbar';
import { SessionExpiredBanner } from '@/components/auth/SessionExpiredBanner';
import './globals.css';

export const metadata: Metadata = {
  title: 'Value Investing WebApp',
  description:
    'Analisi quantitative Graham/Buffett su titoli quotati USA + watchlist personale.',
};

const ANTI_FOUC_SCRIPT = `
(function(){
  try {
    var t = localStorage.getItem('theme');
    var d = (t === 'dark') ||
            (!t && window.matchMedia('(prefers-color-scheme: dark)').matches);
    if (d) document.documentElement.classList.add('dark');
  } catch(e) {}
})();
`;

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
      <head>
        <script
          dangerouslySetInnerHTML={{ __html: ANTI_FOUC_SCRIPT }}
        />
      </head>
      <body>
        <ThemeProvider>
          <AuthProvider>
            <ToastProvider>
              <NotificationProvider>
                <a
                  href="#main-content"
                  className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-primary focus:px-4 focus:py-2 focus:text-on-primary focus:shadow-lg"
                >
                  Vai al contenuto principale
                </a>
                <NotificationContainer />
                <SessionExpiredBanner />
                <Navbar />
                <div id="main-content">
                  {children}
                </div>
              </NotificationProvider>
            </ToastProvider>
          </AuthProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
