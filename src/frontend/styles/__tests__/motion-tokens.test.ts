import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const ROOT = resolve(__dirname, '../..');

function read(relPath: string): string {
  return readFileSync(resolve(ROOT, relPath), 'utf-8');
}

const INTERACTIVE_COMPONENTS = [
  'components/ui/Button.tsx',
  'components/ui/Input.tsx',
  'components/ui/Modal.tsx',
  'components/ui/Toast.tsx',
  'components/layout/Navbar.tsx',
] as const;

describe('Motion Token System — TSK-190', () => {
  describe('motion.css contains all required tokens', () => {
    const motionCss = read('styles/tokens/motion.css');

    it('defines easing tokens', () => {
      expect(motionCss).toContain('--motion-easing-emphasized');
      expect(motionCss).toContain('--motion-easing-standard');
    });

    it('defines duration tokens with correct values', () => {
      expect(motionCss).toMatch(/--motion-duration-short:\s*150ms/);
      expect(motionCss).toMatch(/--motion-duration-medium:\s*300ms/);
      expect(motionCss).toMatch(/--motion-duration-long:\s*500ms/);
    });

    it('defines state-layer opacity tokens with correct values', () => {
      expect(motionCss).toMatch(/--state-hover-opacity:\s*0\.08/);
      expect(motionCss).toMatch(/--state-focus-opacity:\s*0\.12/);
      expect(motionCss).toMatch(/--state-pressed-opacity:\s*0\.16/);
    });

    it('has all tokens inside :root', () => {
      expect(motionCss).toContain(':root');

      const rootBlock = motionCss.match(/:root\s*\{([^}]+)\}/);
      expect(rootBlock).not.toBeNull();

      const rootContent = rootBlock![1];
      expect(rootContent).toContain('--motion-easing-emphasized');
      expect(rootContent).toContain('--motion-easing-standard');
      expect(rootContent).toContain('--motion-duration-short');
      expect(rootContent).toContain('--motion-duration-medium');
      expect(rootContent).toContain('--motion-duration-long');
      expect(rootContent).toContain('--state-hover-opacity');
      expect(rootContent).toContain('--state-focus-opacity');
      expect(rootContent).toContain('--state-pressed-opacity');
    });
  });

  describe('prefers-reduced-motion sets all durations to 0ms', () => {
    const motionCss = read('styles/tokens/motion.css');

    it('contains prefers-reduced-motion media query', () => {
      expect(motionCss).toContain(
        '@media (prefers-reduced-motion: reduce)',
      );
    });

    it('overrides all three duration tokens to 0ms', () => {
      const reducedBlock = motionCss.match(
        /@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{([\s\S]*?)\n\}/,
      );
      expect(reducedBlock, 'Missing reduced-motion block').not.toBeNull();

      const inner = reducedBlock![1];
      expect(inner).toMatch(/--motion-duration-short:\s*0ms/);
      expect(inner).toMatch(/--motion-duration-medium:\s*0ms/);
      expect(inner).toMatch(/--motion-duration-long:\s*0ms/);
    });

    it('does not override easing tokens in reduced-motion', () => {
      const reducedBlock = motionCss.match(
        /@media\s*\(prefers-reduced-motion:\s*reduce\)\s*\{([\s\S]*?)\n\}/,
      );
      const inner = reducedBlock![1];
      expect(inner).not.toContain('--motion-easing');
    });
  });

  describe('state-layer utility in globals.css', () => {
    const globalsCss = read('app/globals.css');

    it('defines .state-layer class', () => {
      expect(globalsCss).toContain('.state-layer');
    });

    it('state-layer uses ::after pseudo-element overlay', () => {
      expect(globalsCss).toContain('.state-layer::after');
    });

    it('hover state references --state-hover-opacity token', () => {
      expect(globalsCss).toContain('.state-layer:hover::after');
      expect(globalsCss).toContain('var(--state-hover-opacity)');
    });

    it('focus-visible state references --state-focus-opacity token', () => {
      expect(globalsCss).toContain('.state-layer:focus-visible::after');
      expect(globalsCss).toContain('var(--state-focus-opacity)');
    });

    it('active/pressed state references --state-pressed-opacity token', () => {
      expect(globalsCss).toContain('.state-layer:active::after');
      expect(globalsCss).toContain('var(--state-pressed-opacity)');
    });

    it('state-layer transition uses motion tokens', () => {
      expect(globalsCss).toMatch(
        /transition:.*var\(--motion-duration-short\).*var\(--motion-easing-standard\)/,
      );
    });
  });

  describe('Button uses state-layer class (not hardcoded hover colors)', () => {
    const buttonSrc = read('components/ui/Button.tsx');

    it('includes state-layer in base classes', () => {
      expect(buttonSrc).toContain('state-layer');
    });

    it('does not use hardcoded hover:bg- color classes', () => {
      const hoverBgMatches = buttonSrc.match(/hover:bg-\w+/g);
      expect(
        hoverBgMatches,
        `Found hardcoded hover colors: ${hoverBgMatches?.join(', ')}`,
      ).toBeNull();
    });

    it('does not use hardcoded hover:opacity- classes', () => {
      const hoverOpacityMatches = buttonSrc.match(/hover:opacity-/g);
      expect(
        hoverOpacityMatches,
        'Found hardcoded hover opacity classes',
      ).toBeNull();
    });
  });

  describe('No inline transition/animation values in migrated components', () => {
    it.each(INTERACTIVE_COMPONENTS)(
      '%s has no inline transition: style declarations',
      (file) => {
        const src = read(file);
        const inlineTransition = src.match(
          /style\s*=\s*\{\s*\{[^}]*transition\s*:/g,
        );
        expect(
          inlineTransition,
          `Found inline transition style in ${file}`,
        ).toBeNull();
      },
    );

    it.each(INTERACTIVE_COMPONENTS)(
      '%s has no inline animation: style declarations',
      (file) => {
        const src = read(file);
        const inlineAnimation = src.match(
          /style\s*=\s*\{\s*\{[^}]*animation\s*:/g,
        );
        expect(
          inlineAnimation,
          `Found inline animation style in ${file}`,
        ).toBeNull();
      },
    );
  });

  describe('focus-visible rule in globals.css', () => {
    const globalsCss = read('app/globals.css');

    it('defines a global :focus-visible rule', () => {
      expect(globalsCss).toMatch(/:focus-visible\s*\{/);
    });

    it('focus-visible uses ring utilities for visible outline', () => {
      const focusBlock = globalsCss.match(
        /:focus-visible\s*\{([^}]+)\}/,
      );
      expect(focusBlock, 'Missing :focus-visible block').not.toBeNull();

      const inner = focusBlock![1];
      expect(inner).toContain('ring-2');
      expect(inner).toContain('ring-primary');
    });
  });

  describe('globals.css imports motion.css', () => {
    const globalsCss = read('app/globals.css');

    it('imports motion.css token file', () => {
      expect(globalsCss).toMatch(/@import\s+['"].*motion\.css['"]/);
    });
  });
});
