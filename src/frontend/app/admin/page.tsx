import { ClientAuthGuard } from '@/components/auth/ClientAuthGuard';
import { LlmBudgetAdminPanel } from '@/components/admin/LlmBudgetAdminPanel';

/**
 * Admin panel — TSK-267 (US-087, ADR-026).
 *
 * Rotta protetta con `roles: ['admin']` nella route-map dichiarativa
 * (`lib/auth/route-config.ts`); `ClientAuthGuard` applica la matrice
 * TSK-266: non autenticato → `/login?returnUrl=/admin`,
 * autenticato senza ruolo admin → `/403`, sessione scaduta → logout
 * silente + `/login?expired=true&returnUrl=/admin`. Il backend resta
 * security boundary autoritativo (defense-in-depth, ADR-025).
 *
 * [^src: design_&_architecture/decisions/ADR-026-frontend-authguard-static-export-runtime.md]
 */
export default function AdminPage(): React.ReactElement {
  return (
    <ClientAuthGuard
      fallback={
        <main className="container mx-auto px-4 py-8 text-center text-sm text-on-surface/60">
          Verifica permessi…
        </main>
      }
    >
      <main className="container mx-auto py-8 px-4">
        <h1 className="text-2xl font-bold mb-6">Admin Panel</h1>
        <LlmBudgetAdminPanel />
      </main>
    </ClientAuthGuard>
  );
}
