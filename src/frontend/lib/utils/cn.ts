import { clsx, type ClassValue } from 'clsx';
import { twMerge } from 'tailwind-merge';

/**
 * Concatena classi Tailwind risolvendo i conflitti (es. `p-2` + `p-4` → `p-4`).
 * Wrapper canonico usato da `class-variance-authority` + componenti UI.
 */
export function cn(...inputs: ClassValue[]): string {
  return twMerge(clsx(inputs));
}
