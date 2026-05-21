import { describe, it, expect } from 'vitest';
import {
  signalClass,
  signalIcon,
  signalLabel,
  signalPresentation,
  type Signal,
} from './signal-color';

describe('signal-color', () => {
  const cases: Array<{ signal: Signal; label: string }> = [
    { signal: 'GREEN', label: 'OK' },
    { signal: 'YELLOW', label: 'Attenzione' },
    { signal: 'RED', label: 'Non soddisfatta' },
    { signal: 'INDETERMINATE', label: 'Indeterminato' },
    { signal: 'NOT_CALCULABLE', label: 'Non calcolabile' },
  ];

  it.each(cases)('mappa $signal con label "$label"', ({ signal, label }) => {
    expect(signalLabel(signal)).toBe(label);
    expect(signalClass(signal)).toMatch(/bg-signal-/);
    expect(signalIcon(signal)).not.toHaveLength(0);
  });

  it('signalPresentation espone tutto in un colpo', () => {
    const presentation = signalPresentation('GREEN');
    expect(presentation.className).toContain('bg-signal-green');
    expect(presentation.label).toBe('OK');
    expect(presentation.icon).toBe('✓');
  });
});
