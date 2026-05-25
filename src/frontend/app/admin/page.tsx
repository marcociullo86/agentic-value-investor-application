import { LlmBudgetAdminPanel } from '@/components/admin/LlmBudgetAdminPanel';

export default function AdminPage() {
  return (
    <main className="container mx-auto py-8 px-4">
      <h1 className="text-2xl font-bold mb-6">Admin Panel</h1>
      <LlmBudgetAdminPanel />
    </main>
  );
}
