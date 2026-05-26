'use client';

import { useEffect, useRef } from 'react';
import type { FieldErrors } from 'react-hook-form';
import { cn } from '@/lib/utils/cn';

interface FormErrorSummaryProps {
  /** react-hook-form `formState.errors` object. */
  readonly errors: FieldErrors;
  /** Maps field names to human-readable labels shown in the summary list. */
  readonly fieldLabels?: Record<string, string>;
  readonly className?: string;
}

function flattenErrors(
  errors: FieldErrors,
  prefix = '',
): Array<{ field: string; message: string }> {
  const result: Array<{ field: string; message: string }> = [];
  for (const [key, value] of Object.entries(errors)) {
    const field = prefix ? `${prefix}.${key}` : key;
    if (value?.message && typeof value.message === 'string') {
      result.push({ field, message: value.message });
    } else if (value && typeof value === 'object' && !value.message) {
      result.push(...flattenErrors(value as FieldErrors, field));
    }
  }
  return result;
}

/**
 * Accessible error summary rendered at the top of a form.
 * Announces errors to screen readers via `aria-live="assertive"` and
 * programmatically focuses itself on mount so assistive tech reads the list.
 * Each error links to the corresponding field input.
 *
 * ADR-022 §5, US-067.
 */
export function FormErrorSummary({
  errors,
  fieldLabels,
  className,
}: FormErrorSummaryProps): React.ReactElement | null {
  const ref = useRef<HTMLDivElement>(null);
  const items = flattenErrors(errors);

  useEffect(() => {
    if (items.length > 0) {
      ref.current?.focus();
    }
  }, [items.length]);

  if (items.length === 0) return null;

  return (
    <div
      ref={ref}
      role="alert"
      aria-live="assertive"
      tabIndex={-1}
      className={cn(
        'rounded-md border border-error/30 bg-error/5 px-4 py-3 text-sm text-error outline-none focus-visible:ring-2 focus-visible:ring-error',
        className,
      )}
    >
      <p className="font-medium">
        {items.length === 1
          ? 'È presente 1 errore nel modulo:'
          : `Sono presenti ${items.length} errori nel modulo:`}
      </p>
      <ul className="mt-1 list-inside list-disc">
        {items.map(({ field, message }) => (
          <li key={field}>
            <a
              href={`#${field}`}
              className="underline hover:text-error/80"
              onClick={(e) => {
                e.preventDefault();
                const el = document.getElementById(field);
                el?.focus();
                el?.scrollIntoView({ behavior: 'smooth', block: 'center' });
              }}
            >
              {fieldLabels?.[field] ?? field}
            </a>
            {' — '}
            {message}
          </li>
        ))}
      </ul>
    </div>
  );
}
