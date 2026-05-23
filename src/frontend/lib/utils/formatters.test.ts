import { describe, it, expect } from 'vitest';
import {
  formatCurrency,
  formatDate,
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

  // US-054 — formatDate non deve mai produrre un numero in formato locale.
  // Regression test del bug originale: 1779484360919 ms diviso 1000 e
  // formattato come Intl.NumberFormat produceva "1.779.484.360,919".
  describe('formatDate', () => {
    it('ISO-8601 string → data leggibile UTC', () => {
      const out = formatDate('2026-05-22T11:12:40Z');
      expect(out).toMatch(/22 mag 2026/);
      expect(out).toMatch(/11:12/);
      expect(out).toMatch(/UTC/);
      // Regressione bug: non deve assomigliare a un numero in italiano
      expect(out).not.toMatch(/^[0-9.,]+$/);
    });

    it('epoch number in ms → data leggibile', () => {
      // 1779484360919 ms = 22 maggio 2026 21:12:40 UTC
      const out = formatDate(1779484360919);
      expect(out).toMatch(/22 mag 2026/);
      expect(out).toMatch(/21:12/);
      // Regressione bug originale: questa era esattamente la stringa visibile
      // nel report TTD prima del fix
      expect(out).not.toBe('1.779.484.360,919');
    });

    it('epoch number in secondi → scalato a ms e formattato', () => {
      // 1779484360 sec = 22 maggio 2026 21:12:40 UTC (stessa data)
      const out = formatDate(1779484360);
      expect(out).toMatch(/22 mag 2026/);
    });

    it('numeric string ms → data leggibile', () => {
      const out = formatDate('1779484360919');
      expect(out).toMatch(/22 mag 2026/);
    });

    it('null/undefined/empty → em-dash', () => {
      expect(formatDate(null)).toBe('—');
      expect(formatDate(undefined)).toBe('—');
      expect(formatDate('')).toBe('—');
    });

    it('input non parsabile → em-dash', () => {
      expect(formatDate('not a date')).toBe('—');
    });
  });
});
