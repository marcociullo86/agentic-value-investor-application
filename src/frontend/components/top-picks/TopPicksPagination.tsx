'use client';

import { useRouter, useSearchParams, usePathname } from 'next/navigation';
import { Button } from '@/components/ui/Button';

/**
 * TopPicksPagination — bottoni prev/next + indicatore "Pagina N di M" (TSK-140).
 *
 * Stato in URL (`page` query param) per deep-linkability.
 * Disabilita bottoni quando ai bordi.
 */

export interface TopPicksPaginationProps {
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

export function TopPicksPagination({
  page,
  size,
  total,
}: TopPicksPaginationProps): React.ReactElement | null {
  const router = useRouter();
  const pathname = usePathname();
  const params = useSearchParams();

  if (total === 0) return null;

  const totalPages = Math.max(1, Math.ceil(total / size));
  const currentPage = page; // 0-indexed
  const displayPage = currentPage + 1; // 1-indexed UI
  const canPrev = currentPage > 0;
  const canNext = currentPage < totalPages - 1;

  const goTo = (nextPage: number): void => {
    const next = new URLSearchParams(params?.toString() ?? '');
    if (nextPage <= 0) {
      next.delete('page');
    } else {
      next.set('page', String(nextPage));
    }
    const qs = next.toString();
    router.replace(qs.length > 0 ? `${pathname}?${qs}` : pathname);
  };

  return (
    <nav
      aria-label="Paginazione classifica"
      data-testid="top-picks-pagination"
      className="flex items-center justify-between gap-3 pt-2 text-sm"
    >
      <span
        className="text-slate-600 dark:text-slate-400"
        data-testid="pagination-indicator"
      >
        Pagina {displayPage} di {totalPages} — {total} risultati
      </span>
      <div className="flex items-center gap-2">
        <Button
          variant="ghost"
          size="sm"
          disabled={!canPrev}
          onClick={() => goTo(currentPage - 1)}
          data-testid="pagination-prev"
        >
          ← Precedente
        </Button>
        <Button
          variant="ghost"
          size="sm"
          disabled={!canNext}
          onClick={() => goTo(currentPage + 1)}
          data-testid="pagination-next"
        >
          Successiva →
        </Button>
      </div>
    </nav>
  );
}
