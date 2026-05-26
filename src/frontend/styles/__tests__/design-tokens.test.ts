import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = resolve(__dirname, '../..');

function read(relPath: string): string {
  return readFileSync(resolve(ROOT, relPath), 'utf-8');
}

const MIGRATED_COMPONENTS = [
  'components/ui/Button.tsx',
  'components/ui/Card.tsx',
  'components/ui/Input.tsx',
  'components/ui/Modal.tsx',
  'components/ui/Toast.tsx',
] as const;

const HARDCODED_COLOR_PATTERN =
  /\b(?:bg|text|border|ring|from|to|via)-(?:blue|slate|gray|zinc|neutral|stone|red|green|yellow|white|black|indigo|purple|pink|orange|emerald|teal|cyan|sky|violet|fuchsia|rose|lime|amber)-\d{2,3}\b/;

const HARDCODED_BARE_COLORS =
  /\b(?:bg|text|border)-(?:white|black)\b(?!\/)/;

describe('Design Token System — TSK-186', () => {
  describe('No hardcoded Tailwind color classes in migrated components', () => {
    it.each(MIGRATED_COMPONENTS)(
      '%s has no hardcoded palette colors',
      (file) => {
        const src = read(file);
        const matches = src.match(new RegExp(HARDCODED_COLOR_PATTERN, 'g'));
        expect(
          matches,
          `Found hardcoded color classes: ${matches?.join(', ')}`,
        ).toBeNull();
      },
    );

    it.each(MIGRATED_COMPONENTS)(
      '%s has no bare bg-white / bg-black / text-white / text-black',
      (file) => {
        const src = read(file);
        const matches = src.match(new RegExp(HARDCODED_BARE_COLORS, 'g'));
        expect(
          matches,
          `Found bare color classes: ${matches?.join(', ')}`,
        ).toBeNull();
      },
    );
  });

  describe('Semantic token classes are used in migrated components', () => {
    const SEMANTIC_EXPECTATIONS: Record<string, string[]> = {
      'components/ui/Button.tsx': [
        'bg-primary',
        'text-on-primary',
        'bg-error',
        'text-on-error',
        'bg-surface-container',
        'text-on-surface',
      ],
      'components/ui/Card.tsx': [
        'bg-surface-container',
        'border-outline-variant',
        'rounded-md',
      ],
      'components/ui/Input.tsx': [
        'bg-surface',
        'border-outline',
        'rounded-md',
      ],
      'components/ui/Modal.tsx': [
        'bg-on-surface/50',
        'bg-surface',
        'border-outline-variant',
      ],
      'components/ui/Toast.tsx': [
        'bg-surface-container',
        'border-outline-variant',
        'rounded-md',
      ],
    };

    for (const [file, expected] of Object.entries(SEMANTIC_EXPECTATIONS)) {
      it(`${file} uses semantic classes: ${expected.join(', ')}`, () => {
        const src = read(file);
        for (const cls of expected) {
          expect(src, `Missing class "${cls}" in ${file}`).toContain(cls);
        }
      });
    }
  });

  describe('Token CSS files structure', () => {
    it('colors.css contains 19 OKLCH semantic color variables', () => {
      const css = read('styles/tokens/colors.css');

      expect(css).toContain(':root');
      expect(css).toContain('oklch(');

      const expectedVars = [
        '--color-primary',
        '--color-on-primary',
        '--color-primary-container',
        '--color-on-primary-container',
        '--color-secondary',
        '--color-on-secondary',
        '--color-tertiary',
        '--color-on-tertiary',
        '--color-surface',
        '--color-on-surface',
        '--color-surface-container',
        '--color-surface-container-high',
        '--color-outline',
        '--color-outline-variant',
        '--color-error',
        '--color-on-error',
        '--color-success',
        '--color-warning',
        '--color-info',
      ];

      for (const v of expectedVars) {
        expect(css, `Missing CSS variable ${v}`).toContain(v);
      }

      const varMatches = css.match(/--color-[\w-]+\s*:/g) ?? [];
      expect(varMatches).toHaveLength(expectedVars.length);
    });

    it('typography.css defines the 5-level type scale', () => {
      const css = read('styles/tokens/typography.css');

      expect(css).toContain(':root');

      const levels = ['display', 'headline', 'title', 'body', 'label'];
      for (const level of levels) {
        expect(css, `Missing typography level: ${level}`).toContain(
          `--typography-${level}`,
        );
      }

      const varMatches = css.match(/--typography-[\w-]+\s*:/g) ?? [];
      expect(varMatches).toHaveLength(levels.length);
    });

    it('shape.css defines all 5 shape tokens', () => {
      const css = read('styles/tokens/shape.css');

      expect(css).toContain(':root');

      const shapes = ['none', 'small', 'medium', 'large', 'full'];
      for (const shape of shapes) {
        expect(css, `Missing shape token: ${shape}`).toContain(
          `--shape-${shape}`,
        );
      }

      const varMatches = css.match(/--shape-[\w-]+\s*:/g) ?? [];
      expect(varMatches).toHaveLength(shapes.length);
    });
  });

  describe('Typography scale consistency', () => {
    it('each typography level uses font shorthand (weight size/line-height family)', () => {
      const css = read('styles/tokens/typography.css');
      const levels = ['display', 'headline', 'title', 'body', 'label'];

      for (const level of levels) {
        const re = new RegExp(
          `--typography-${level}:\\s*\\d+\\s+[\\d.]+rem/[\\d.]+rem\\s+var\\(--font-family-base`,
        );
        expect(css, `Typography ${level} not in shorthand format`).toMatch(re);
      }
    });

    it('display has the largest font-size', () => {
      const css = read('styles/tokens/typography.css');
      const sizePattern = /--typography-(\w+):\s*\d+\s+([\d.]+)rem/g;
      const sizes: Record<string, number> = {};

      let m: RegExpExecArray | null;
      while ((m = sizePattern.exec(css)) !== null) {
        sizes[m[1]] = parseFloat(m[2]);
      }

      expect(sizes['display']).toBeGreaterThan(sizes['headline']);
      expect(sizes['headline']).toBeGreaterThanOrEqual(sizes['title']);
      expect(sizes['title']).toBeGreaterThan(sizes['body']);
      expect(sizes['body']).toBeGreaterThan(sizes['label']);
    });
  });

  describe('Shape tokens consistency', () => {
    it('shape.none is 0 and shape.full is 9999px', () => {
      const css = read('styles/tokens/shape.css');

      expect(css).toMatch(/--shape-none:\s*0\b/);
      expect(css).toMatch(/--shape-full:\s*9999px/);
    });

    it('shape scale is monotonically increasing', () => {
      const css = read('styles/tokens/shape.css');
      const ordered = ['small', 'medium', 'large'];
      const values: number[] = [];

      for (const s of ordered) {
        const m = css.match(new RegExp(`--shape-${s}:\\s*([\\d.]+)rem`));
        expect(m, `shape.${s} should be in rem`).not.toBeNull();
        values.push(parseFloat(m![1]));
      }

      for (let i = 1; i < values.length; i++) {
        expect(values[i]).toBeGreaterThan(values[i - 1]);
      }
    });

    it('Card uses rounded-md (mapped to shape.medium)', () => {
      const src = read('components/ui/Card.tsx');
      expect(src).toContain('rounded-md');
    });
  });

  describe('Tailwind config maps tokens correctly', () => {
    it('borderRadius entries reference CSS shape variables', () => {
      const cfg = read('tailwind.config.ts');

      expect(cfg).toContain("sm: 'var(--shape-small)'");
      expect(cfg).toContain("md: 'var(--shape-medium)'");
      expect(cfg).toContain("lg: 'var(--shape-large)'");
      expect(cfg).toContain("full: 'var(--shape-full)'");
    });

    it('color entries reference CSS color variables', () => {
      const cfg = read('tailwind.config.ts');

      const semanticColorKeys = [
        'primary',
        'on-primary',
        'secondary',
        'surface',
        'on-surface',
        'outline',
        'error',
      ];

      for (const key of semanticColorKeys) {
        expect(
          cfg,
          `Missing var(--color-${key}) mapping`,
        ).toContain(`var(--color-${key})`);
      }
    });
  });
});
