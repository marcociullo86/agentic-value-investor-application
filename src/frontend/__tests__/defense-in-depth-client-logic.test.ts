/**
 * US-079 / TSK-220 — AC: no critical valuation logic as client-side source of truth.
 *
 * Static guard: Graham Number, Margin of Safety, and DCF intrinsic value must be
 * computed on the backend; the FE may only display API-provided fields.
 */
import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';

const FRONTEND_ROOT = resolve(__dirname, '..');

const SCAN_ROOTS = ['components', 'lib'] as const;

const SKIP_DIR_NAMES = new Set(['__tests__', 'e2e', 'node_modules', '.next']);

const SKIP_FILE_PATTERN =
  /\.(test|spec)\.(ts|tsx)$|\.stories\.(ts|tsx)$|fixtures\//;

/** Patterns that indicate client-side valuation computation (not display/format). */
const FORBIDDEN_VALUATION_PATTERNS: ReadonlyArray<{
  readonly name: string;
  readonly pattern: RegExp;
}> = [
  {
    name: 'Graham formula constant 22.5',
    pattern: /\b22\.5\s*\*\s*\(/,
  },
  {
    name: 'calculateGraham / computeMos helpers',
    pattern: /\bfunction\s+(calculate|compute)(Graham|Mos|MarginOfSafety|DcfIntrinsic)\b/i,
  },
  {
    name: 'sqrt-based Graham intrinsic',
    pattern: /Math\.sqrt\s*\([^)]*(?:eps|bvps|earnings)/i,
  },
];

function collectSourceFiles(dir: string, acc: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      if (SKIP_DIR_NAMES.has(entry)) continue;
      collectSourceFiles(full, acc);
      continue;
    }
    if (!/\.(ts|tsx)$/.test(entry)) continue;
    const rel = full.slice(FRONTEND_ROOT.length + 1).replace(/\\/g, '/');
    if (SKIP_FILE_PATTERN.test(rel)) continue;
    acc.push(full);
  }
  return acc;
}

function scanFrontendSources(): string[] {
  const files: string[] = [];
  for (const root of SCAN_ROOTS) {
    collectSourceFiles(join(FRONTEND_ROOT, root), files);
  }
  return files;
}

describe('Defense in depth — no client-only valuation logic (TSK-220)', () => {
  const sources = scanFrontendSources();

  it('scans components/ and lib/ (excludes tests and e2e fixtures)', () => {
    expect(sources.length).toBeGreaterThan(10);
  });

  it.each(FORBIDDEN_VALUATION_PATTERNS)(
    'no source file matches forbidden pattern: $name',
    ({ pattern }) => {
      const violations: string[] = [];
      for (const file of sources) {
        const content = readFileSync(file, 'utf-8');
        if (pattern.test(content)) {
          violations.push(file.slice(FRONTEND_ROOT.length + 1));
        }
      }
      expect(
        violations,
        `Client-side valuation logic detected in:\n${violations.join('\n')}`,
      ).toEqual([]);
    },
  );
});
