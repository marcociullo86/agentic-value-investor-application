import { describe, it, expect } from 'vitest';
import {
  formatCurrency,
  formatMarketCap,
  formatPercent,
  formatRatio,
} from './formatters';

describe('formatters', () => {
  it('formatCurrency con default USD', () => {
    // Node 20 ICU on the CI runner emits "1234,50 USD" (no thousands separator,
    // USD code instead of $ symbol). Local full-ICU emits "1.234,50 US$". The
    // contract is "decimal sep is comma, currency identifier is USD/US$/$" —
    // thousand separator presence is incidental, so the regex tolerates both.
    expect(formatCurrency(1234.5)).toMatch(/1\.?234,50.*(USD|US\$|\$)/);
  });

  it('formatPercent con 2 decimali', () => {
    // 0.1234 -> "12,34 %" (it-IT)
    expect(formatPercent(0.1234)).toMatch(/12,34/);
  });

  it('formatMarketCap su scale T/B/M/K', () => {
    expect(formatMarketCap(2.3e12)).toBe('$2.30T');
    expect(formatMarketCap(2.3e9)).toBe('$2.30B');
    expect(formatMarketCap(2.3e6)).toBe('$2.30M');
    expect(formatMarketCap(2300)).toBe('$2.30K');
    expect(formatMarketCap(123)).toBe('$123.00');
  });

  it('formatRatio decimali configurabili', () => {
    expect(formatRatio(1.2345, 3)).toMatch(/1,235|1,234/);
  });

  it('valori non finiti rendono em-dash', () => {
    expect(formatCurrency(Number.NaN)).toBe('—');
    expect(formatPercent(Number.POSITIVE_INFINITY)).toBe('—');
    expect(formatMarketCap(Number.NaN)).toBe('—');
  });
});
