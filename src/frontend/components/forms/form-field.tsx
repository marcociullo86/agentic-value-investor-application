'use client';

import type { ReactNode } from 'react';
import { cn } from '@/lib/utils/cn';

interface FormFieldProps {
  /** Field name — used to derive the error message id (`${name}-error`). */
  readonly name: string;
  readonly label: string;
  /** Error message for this field (from react-hook-form). */
  readonly error?: string;
  readonly children: ReactNode;
  readonly className?: string;
}

/**
 * Wrapper that renders a label, the field input (children), and an optional
 * inline error message linked via `aria-describedby`.
 *
 * The caller must pass `id={name}` and `aria-describedby={`${name}-error`}`
 * to the input element, or use the companion `Input` component which accepts
 * these props.
 */
export function FormField({
  name,
  label,
  error,
  children,
  className,
}: FormFieldProps): React.ReactElement {
  return (
    <div className={cn('flex flex-col gap-1 text-sm', className)}>
      <label htmlFor={name} className="font-medium">
        {label}
      </label>
      {children}
      {error && (
        <p
          id={`${name}-error`}
          role="alert"
          className="text-error text-xs"
        >
          {error}
        </p>
      )}
    </div>
  );
}
