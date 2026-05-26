import * as React from 'react';
import { cn } from '@/lib/utils/cn';

export interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  /** Quando true, mostra ring rosso (consumato da react-hook-form). */
  readonly error?: boolean;
}

/**
 * Input controllato/uncontrolled compatibile con react-hook-form
 * (TSK-006/034 useranno `register`).
 */
export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ className, error, type = 'text', ...props }, ref) => {
    return (
      <input
        ref={ref}
        type={type}
        aria-invalid={error || undefined}
        className={cn(
          'flex h-10 w-full rounded-md border bg-surface px-3 py-2 text-sm shadow-sm transition-colors placeholder:text-on-surface/40 disabled:cursor-not-allowed disabled:opacity-50',
          error
            ? 'border-error focus-visible:ring-error'
            : 'border-outline',
          className,
        )}
        {...props}
      />
    );
  },
);
Input.displayName = 'Input';
