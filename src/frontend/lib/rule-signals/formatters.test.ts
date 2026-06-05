/**
 * Test suite formatRuleSignal() — TSK-321 (US-095 / EP-021, ADR-028 §6).
 *
 * Copertura: 13 ruleId EP-021 × 3 stati visivi (GREEN-equivalent, RED/YELLOW-equivalent,
 * INDETERMINATE/NOT_CALCULABLE con campi null) = ~41 unit test.
 * Più 2 ruleId EP-023 (NCAV_LATEST / NET_NET_RATIO) con stessi 3 stati.
 * Sezione "fallback paranoid esplicito" verifica comportamento con rationale assente → "N/A".
 * Sezione "runtime drift" verifica sicurezza su ruleId sconosciuto.
 * Sezione "matrix sweep" assicura title/subtitle/tooltip non vuoti per tutti i 13 ruleId GREEN.
 *
 * Convenzioni fixture:
 *  - GREEN: campi tipati valorizzati con valori che soddisfano la soglia.
 *  - RED (o YELLOW dove applicabile): campi tipati valorizzati che non soddisfano la soglia.
 *  - INDETERMINATE / NOT_CALCULABLE: campo principale a null → fallback paranoid attivo.
 *
 * Importazione via re-export pubblico formatters.ts (RuleSignal union discriminata).
 *
 * Riferimenti:
 *  - TSK-319 (formatters.ts), TSK-320 (RuleSignalCard / TrafficLightPanel migration),
 *    TSK-321 (questo task QA), ADR-028 §3/§5/§6.
 */

import { describe, it, expect } from 'vitest';
import { formatRuleSignal } from './formatters';
import type { RuleSignal } from './formatters';

// ---------------------------------------------------------------------------
// Costante base comune a tutti i fixture (campi legacy deprecati).
// ---------------------------------------------------------------------------
const BASE_LEGACY = {
  signal: 'GREEN' as const,
  rationale: 'legacy rationale fallback text',
  observedValue: null as number | null,
  threshold: 'legacy threshold text',
};

// ---------------------------------------------------------------------------
// 1. SIZE_LATEST
// ---------------------------------------------------------------------------
describe('formatRuleSignal — SIZE_LATEST', () => {
  it('GREEN: revenueLatest valorizzato → title "Dimensione", subtitle contiene valore e soglia', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'SIZE_LATEST',
      signal: 'GREEN',
      revenueLatest: 2_300_000_000,
      thresholdUsd: 100_000_000,
    };
    const { title, subtitle, tooltip } = formatRuleSignal(s);
    expect(title).toBe('Dimensione');
    expect(subtitle).toContain('$2.30B');
    expect(subtitle).toContain('$100.00M');
    expect(tooltip).toMatch(/Graham/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: revenueLatest sotto soglia → subtitle non vuoto contiene valore', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'SIZE_LATEST',
      signal: 'RED',
      revenueLatest: 50_000_000,
      thresholdUsd: 100_000_000,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('$50.00M');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: revenueLatest=null → fallback su rationale legacy', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'SIZE_LATEST',
      signal: 'INDETERMINATE',
      revenueLatest: null,
      thresholdUsd: 100_000_000,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 2. EARNINGS_STABILITY_10Y
// ---------------------------------------------------------------------------
describe('formatRuleSignal — EARNINGS_STABILITY_10Y', () => {
  it('GREEN: 10/10 anni positivi, lossYears=[] → subtitle mostra "10/10 anni positivi"', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'EARNINGS_STABILITY_10Y',
      signal: 'GREEN',
      yearsPositive: 10,
      yearsAvailable: 10,
      lossYears: [],
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Stabilita earnings');
    expect(subtitle).toContain('10/10');
    expect(subtitle).not.toContain('loss year');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: anni con perdita → subtitle mostra count loss years', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'EARNINGS_STABILITY_10Y',
      signal: 'RED',
      yearsPositive: 7,
      yearsAvailable: 10,
      lossYears: [2016, 2019, 2020],
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('7/10');
    expect(subtitle).toContain('3 loss years');
  });

  it('INDETERMINATE: yearsAvailable=0 + lossYears=[] → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'EARNINGS_STABILITY_10Y',
      signal: 'INDETERMINATE',
      yearsPositive: 0,
      yearsAvailable: 0,
      lossYears: [],
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 3. EPS_GROWTH_10Y
// ---------------------------------------------------------------------------
describe('formatRuleSignal — EPS_GROWTH_10Y', () => {
  it('GREEN: cagrPercent valorizzato → subtitle contiene CAGR % e soglia %', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'EPS_GROWTH_10Y',
      signal: 'GREEN',
      cagrPercent: 8.5,
      thresholdPercent: 2.9,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Crescita EPS');
    expect(subtitle).toContain('8.5%');
    expect(subtitle).toContain('2.9%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: cagrPercent negativo → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'EPS_GROWTH_10Y',
      signal: 'RED',
      cagrPercent: -1.2,
      thresholdPercent: 2.9,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('-1.2%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: cagrPercent=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'EPS_GROWTH_10Y',
      signal: 'INDETERMINATE',
      cagrPercent: null,
      thresholdPercent: 2.9,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 4. PE_3Y_AVG
// ---------------------------------------------------------------------------
describe('formatRuleSignal — PE_3Y_AVG', () => {
  it('GREEN: pe3yAvg <= thresholdGreen → subtitle mostra P/E e soglie', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'PE_3Y_AVG',
      signal: 'GREEN',
      pe3yAvg: 12.5,
      thresholdGreen: 15,
      thresholdYellow: 20,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('P/E moderato');
    // fmtRatio(12.5, 2) locale it-IT → "12,50"
    expect(subtitle).toMatch(/12/);
    expect(subtitle).toMatch(/15/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('YELLOW: pe3yAvg tra green e yellow → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'PE_3Y_AVG',
      signal: 'YELLOW',
      pe3yAvg: 17.0,
      thresholdGreen: 15,
      thresholdYellow: 20,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toMatch(/17/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: pe3yAvg=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'PE_3Y_AVG',
      signal: 'INDETERMINATE',
      pe3yAvg: null,
      thresholdGreen: 15,
      thresholdYellow: 20,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 5. PB_LATEST
// ---------------------------------------------------------------------------
describe('formatRuleSignal — PB_LATEST', () => {
  it('GREEN: pbLatest <= thresholdGreen → subtitle contiene P/B e soglie', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'PB_LATEST',
      signal: 'GREEN',
      pbLatest: 1.2,
      thresholdGreen: 1.5,
      thresholdYellow: 3.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('P/B moderato');
    expect(subtitle).toMatch(/1/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('YELLOW: pbLatest tra green e yellow → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'PB_LATEST',
      signal: 'YELLOW',
      pbLatest: 2.2,
      thresholdGreen: 1.5,
      thresholdYellow: 3.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toMatch(/2/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: pbLatest=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'PB_LATEST',
      signal: 'INDETERMINATE',
      pbLatest: null,
      thresholdGreen: 1.5,
      thresholdYellow: 3.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 6. DIVIDEND_CONTINUITY_20Y
// ---------------------------------------------------------------------------
describe('formatRuleSignal — DIVIDEND_CONTINUITY_20Y', () => {
  it('GREEN: 25 anni consecutivi → subtitle contiene anni e soglia', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'DIVIDEND_CONTINUITY_20Y',
      signal: 'GREEN',
      consecutiveYears: 25,
      thresholdYears: 20,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Dividendo continuo');
    expect(subtitle).toContain('25');
    expect(subtitle).toContain('20y');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: soli 5 anni consecutivi → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'DIVIDEND_CONTINUITY_20Y',
      signal: 'RED',
      consecutiveYears: 5,
      thresholdYears: 20,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('5');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: consecutiveYears=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'DIVIDEND_CONTINUITY_20Y',
      signal: 'INDETERMINATE',
      consecutiveYears: null,
      thresholdYears: 20,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 7. ROE_10Y_AVG
// ---------------------------------------------------------------------------
describe('formatRuleSignal — ROE_10Y_AVG', () => {
  it('GREEN: averagePercent >= thresholdGreen → subtitle mostra % e soglie', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'ROE_10Y_AVG',
      signal: 'GREEN',
      averagePercent: 18.5,
      yearsAvailable: 10,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('ROE 10y');
    expect(subtitle).toContain('18.5%');
    expect(subtitle).toContain('15.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('YELLOW: averagePercent tra thresholdYellow e thresholdGreen → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'ROE_10Y_AVG',
      signal: 'YELLOW',
      averagePercent: 12.0,
      yearsAvailable: 10,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('12.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: averagePercent=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'ROE_10Y_AVG',
      signal: 'INDETERMINATE',
      averagePercent: null,
      yearsAvailable: 0,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 8. ROIC_10Y_AVG
// ---------------------------------------------------------------------------
describe('formatRuleSignal — ROIC_10Y_AVG', () => {
  it('GREEN: averagePercent >= thresholdGreen → title "ROIC 10y", subtitle con %', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'ROIC_10Y_AVG',
      signal: 'GREEN',
      averagePercent: 20.0,
      yearsAvailable: 10,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('ROIC 10y');
    expect(subtitle).toContain('20.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: averagePercent < thresholdYellow → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'ROIC_10Y_AVG',
      signal: 'RED',
      averagePercent: 5.0,
      yearsAvailable: 10,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('5.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: averagePercent=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'ROIC_10Y_AVG',
      signal: 'INDETERMINATE',
      averagePercent: null,
      yearsAvailable: 0,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 9. GROSS_MARGIN_10Y_AVG
// ---------------------------------------------------------------------------
describe('formatRuleSignal — GROSS_MARGIN_10Y_AVG', () => {
  it('GREEN: averagePercent >= 40 → title "Gross margin 10y", subtitle con %', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'GROSS_MARGIN_10Y_AVG',
      signal: 'GREEN',
      averagePercent: 45.0,
      thresholdGreenPercent: 40.0,
      thresholdYellowPercent: 25.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Gross margin 10y');
    expect(subtitle).toContain('45.0%');
    expect(subtitle).toContain('40.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('YELLOW: averagePercent tra yellow e green → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'GROSS_MARGIN_10Y_AVG',
      signal: 'YELLOW',
      averagePercent: 30.0,
      thresholdGreenPercent: 40.0,
      thresholdYellowPercent: 25.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('30.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: averagePercent=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'GROSS_MARGIN_10Y_AVG',
      signal: 'INDETERMINATE',
      averagePercent: null,
      thresholdGreenPercent: 40.0,
      thresholdYellowPercent: 25.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 10. NET_MARGIN_10Y_AVG
// ---------------------------------------------------------------------------
describe('formatRuleSignal — NET_MARGIN_10Y_AVG', () => {
  it('GREEN: averagePercent >= thresholdGreen → title "Net margin 10y", subtitle con %', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_MARGIN_10Y_AVG',
      signal: 'GREEN',
      averagePercent: 22.0,
      thresholdGreenPercent: 20.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Net margin 10y');
    expect(subtitle).toContain('22.0%');
    expect(subtitle).toContain('20.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: averagePercent < thresholdGreen → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_MARGIN_10Y_AVG',
      signal: 'RED',
      averagePercent: 5.0,
      thresholdGreenPercent: 20.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('5.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: averagePercent=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_MARGIN_10Y_AVG',
      signal: 'INDETERMINATE',
      averagePercent: null,
      thresholdGreenPercent: 20.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 11. CURRENT_RATIO_LATEST
// ---------------------------------------------------------------------------
describe('formatRuleSignal — CURRENT_RATIO_LATEST', () => {
  it('GREEN: ratioLatest >= thresholdGreen → title "Current ratio", subtitle con ratio e soglie', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'CURRENT_RATIO_LATEST',
      signal: 'GREEN',
      ratioLatest: 2.5,
      thresholdGreen: 2.0,
      thresholdYellow: 1.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Current ratio');
    expect(subtitle).toMatch(/2/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('YELLOW: ratioLatest tra yellow e green → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'CURRENT_RATIO_LATEST',
      signal: 'YELLOW',
      ratioLatest: 1.5,
      thresholdGreen: 2.0,
      thresholdYellow: 1.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: ratioLatest=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'CURRENT_RATIO_LATEST',
      signal: 'INDETERMINATE',
      ratioLatest: null,
      thresholdGreen: 2.0,
      thresholdYellow: 1.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 12. DEBT_TO_INCOME_LATEST
// ---------------------------------------------------------------------------
describe('formatRuleSignal — DEBT_TO_INCOME_LATEST', () => {
  it('GREEN: netIncomePositive=true + ratioLatest <= thresholdGreen → subtitle non vuoto con ratio', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'DEBT_TO_INCOME_LATEST',
      signal: 'GREEN',
      ratioLatest: 2.5,
      thresholdGreen: 4.0,
      thresholdYellow: 8.0,
      netIncomePositive: true,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Debt / income');
    expect(subtitle).toMatch(/2/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE — edge case ADR-028 §3: netIncomePositive=false → subtitle "Net income <= 0"', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'DEBT_TO_INCOME_LATEST',
      signal: 'INDETERMINATE',
      ratioLatest: null,
      thresholdGreen: 4.0,
      thresholdYellow: 8.0,
      netIncomePositive: false,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('Net income');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: netIncomePositive=true + ratioLatest=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'DEBT_TO_INCOME_LATEST',
      signal: 'INDETERMINATE',
      ratioLatest: null,
      thresholdGreen: 4.0,
      thresholdYellow: 8.0,
      netIncomePositive: true,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// 13. CAPEX_INTENSITY_10Y_AVG
// ---------------------------------------------------------------------------
describe('formatRuleSignal — CAPEX_INTENSITY_10Y_AVG', () => {
  it('GREEN: averagePercent <= thresholdGreen → title "Capex intensity 10y", subtitle con %', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'CAPEX_INTENSITY_10Y_AVG',
      signal: 'GREEN',
      averagePercent: 18.0,
      thresholdGreenPercent: 25.0,
      thresholdYellowPercent: 50.0,
    };
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('Capex intensity 10y');
    expect(subtitle).toContain('18.0%');
    expect(subtitle).toContain('25.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: averagePercent > thresholdYellow → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'CAPEX_INTENSITY_10Y_AVG',
      signal: 'RED',
      averagePercent: 65.0,
      thresholdGreenPercent: 25.0,
      thresholdYellowPercent: 50.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('65.0%');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: averagePercent=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'CAPEX_INTENSITY_10Y_AVG',
      signal: 'INDETERMINATE',
      averagePercent: null,
      thresholdGreenPercent: 25.0,
      thresholdYellowPercent: 50.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// EP-023 — NCAV_LATEST
// ---------------------------------------------------------------------------
describe('formatRuleSignal — NCAV_LATEST (EP-023)', () => {
  it('GREEN: ncavTotal e ncavPerShare valorizzati → title "NCAV", subtitle contiene "$" e "per azione"', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NCAV_LATEST',
      signal: 'GREEN',
      ncavTotal: 5_000_000_000,
      ncavPerShare: 25.50,
    };
    const { title, subtitle, tooltip } = formatRuleSignal(s);
    expect(title).toBe('NCAV');
    expect(subtitle).toContain('$5.00B');
    expect(subtitle).toContain('$25.50');
    expect(subtitle).toMatch(/per azione/i);
    expect(tooltip).toMatch(/Graham Cap\.15/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: ncavTotal negativo → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NCAV_LATEST',
      signal: 'RED',
      ncavTotal: -2_000_000_000,
      ncavPerShare: -10.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: ncavTotal=null → fallback su rationale legacy', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NCAV_LATEST',
      signal: 'INDETERMINATE',
      ncavTotal: null,
      ncavPerShare: null,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// EP-023 — NET_NET_RATIO
// ---------------------------------------------------------------------------
describe('formatRuleSignal — NET_NET_RATIO (EP-023)', () => {
  it('GREEN: ratio < soglia → title "Net-Net ratio", subtitle contiene "Ratio" + valore + "soglia"', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_NET_RATIO',
      signal: 'GREEN',
      priceLatest: 10.0,
      ncavPerShare: 25.0,
      ratio: 0.4,
      thresholdRatio: 0.6667,
    };
    const { title, subtitle, tooltip } = formatRuleSignal(s);
    expect(title).toBe('Net-Net ratio');
    // fmtRatio usa Intl.NumberFormat('it-IT') → separatore decimale virgola
    // ("0,4000"); il matcher tollera entrambi i separatori per robustezza locale.
    expect(subtitle).toMatch(/0[.,]4000/);
    expect(subtitle).toMatch(/soglia/i);
    expect(tooltip).toMatch(/Graham Cap\.15/);
    expect(tooltip).toContain('0.6667');
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('RED: ratio >= soglia → subtitle non vuoto', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_NET_RATIO',
      signal: 'RED',
      priceLatest: 50.0,
      ncavPerShare: 25.0,
      ratio: 2.0,
      thresholdRatio: 0.6667,
    };
    const { subtitle } = formatRuleSignal(s);
    // it-IT → "2,0000"; matcher tollerante al separatore decimale.
    expect(subtitle).toMatch(/2[.,]0000/);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('INDETERMINATE: ratio=null + priceLatest e ncavPerShare valorizzati → subtitle con Prezzo e NCAV', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_NET_RATIO',
      signal: 'INDETERMINATE',
      priceLatest: 30.0,
      ncavPerShare: 20.0,
      ratio: null,
      thresholdRatio: 0.6667,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toContain('$30.00');
    expect(subtitle).toMatch(/NCAV/i);
    expect(subtitle.length).toBeGreaterThan(0);
  });

  it('NOT_CALCULABLE: ratio=null + priceLatest=null + ncavPerShare=null → fallback paranoid', () => {
    const s: RuleSignal = {
      ...BASE_LEGACY,
      ruleId: 'NET_NET_RATIO',
      signal: 'NOT_CALCULABLE',
      priceLatest: null,
      ncavPerShare: null,
      ratio: null,
      thresholdRatio: 0.6667,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('legacy rationale fallback text');
  });
});

// ---------------------------------------------------------------------------
// Fallback paranoid esplicito — rationale assente → "N/A"
// ---------------------------------------------------------------------------
describe('formatRuleSignal — fallback paranoid con rationale assente → "N/A"', () => {
  it('SIZE_LATEST: revenueLatest=null + rationale="" → "N/A"', () => {
    const s: RuleSignal = {
      ruleId: 'SIZE_LATEST',
      signal: 'INDETERMINATE',
      revenueLatest: null,
      thresholdUsd: 100_000_000,
      rationale: '',
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('N/A');
  });

  it('EPS_GROWTH_10Y: cagrPercent=null + rationale omesso → "N/A"', () => {
    const s: RuleSignal = {
      ruleId: 'EPS_GROWTH_10Y',
      signal: 'INDETERMINATE',
      cagrPercent: null,
      thresholdPercent: 2.9,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('N/A');
  });

  it('ROE_10Y_AVG: averagePercent=null + rationale omesso → "N/A"', () => {
    const s: RuleSignal = {
      ruleId: 'ROE_10Y_AVG',
      signal: 'INDETERMINATE',
      averagePercent: null,
      yearsAvailable: 0,
      thresholdGreenPercent: 15.0,
      thresholdYellowPercent: 10.0,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('N/A');
  });

  it('NCAV_LATEST: ncavTotal=null + rationale omesso → "N/A"', () => {
    const s: RuleSignal = {
      ruleId: 'NCAV_LATEST',
      signal: 'INDETERMINATE',
      ncavTotal: null,
      ncavPerShare: null,
    };
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('N/A');
  });
});

// ---------------------------------------------------------------------------
// Runtime safety — ruleId sconosciuto (drift schema BE vs client TS)
// ---------------------------------------------------------------------------
describe('formatRuleSignal — runtime fallback su ruleId sconosciuto', () => {
  it('ruleId ignoto → non lancia eccezione, title = ruleId raw, subtitle = rationale', () => {
    const s = {
      ruleId: 'UNKNOWN_FUTURE_RULE',
      signal: 'GREEN',
      rationale: 'testo rationale future rule',
    } as unknown as RuleSignal;

    expect(() => formatRuleSignal(s)).not.toThrow();
    const { title, subtitle } = formatRuleSignal(s);
    expect(title).toBe('UNKNOWN_FUTURE_RULE');
    expect(subtitle).toBe('testo rationale future rule');
  });

  it('ruleId ignoto + rationale assente → subtitle = "N/A", nessun crash', () => {
    const s = {
      ruleId: 'ANOTHER_UNKNOWN_RULE',
      signal: 'INDETERMINATE',
    } as unknown as RuleSignal;

    expect(() => formatRuleSignal(s)).not.toThrow();
    const { subtitle } = formatRuleSignal(s);
    expect(subtitle).toBe('N/A');
  });
});

// ---------------------------------------------------------------------------
// Matrix sweep — tutti i 13 ruleId EP-021 producono title/subtitle/tooltip non vuoti
// ---------------------------------------------------------------------------
describe('formatRuleSignal — matrix sweep 13 ruleId EP-021 GREEN: title/subtitle/tooltip non vuoti', () => {
  const ALL_EP021_GREEN: RuleSignal[] = [
    { ruleId: 'SIZE_LATEST', signal: 'GREEN', revenueLatest: 5e9, thresholdUsd: 1e8 },
    { ruleId: 'EARNINGS_STABILITY_10Y', signal: 'GREEN', yearsPositive: 10, yearsAvailable: 10, lossYears: [] },
    { ruleId: 'EPS_GROWTH_10Y', signal: 'GREEN', cagrPercent: 5.0, thresholdPercent: 2.9 },
    { ruleId: 'PE_3Y_AVG', signal: 'GREEN', pe3yAvg: 12.0, thresholdGreen: 15, thresholdYellow: 20 },
    { ruleId: 'PB_LATEST', signal: 'GREEN', pbLatest: 1.0, thresholdGreen: 1.5, thresholdYellow: 3.0 },
    { ruleId: 'DIVIDEND_CONTINUITY_20Y', signal: 'GREEN', consecutiveYears: 25, thresholdYears: 20 },
    { ruleId: 'ROE_10Y_AVG', signal: 'GREEN', averagePercent: 20.0, yearsAvailable: 10, thresholdGreenPercent: 15, thresholdYellowPercent: 10 },
    { ruleId: 'ROIC_10Y_AVG', signal: 'GREEN', averagePercent: 18.0, yearsAvailable: 10, thresholdGreenPercent: 15, thresholdYellowPercent: 10 },
    { ruleId: 'GROSS_MARGIN_10Y_AVG', signal: 'GREEN', averagePercent: 45.0, thresholdGreenPercent: 40, thresholdYellowPercent: 25 },
    { ruleId: 'NET_MARGIN_10Y_AVG', signal: 'GREEN', averagePercent: 22.0, thresholdGreenPercent: 20 },
    { ruleId: 'CURRENT_RATIO_LATEST', signal: 'GREEN', ratioLatest: 3.0, thresholdGreen: 2.0, thresholdYellow: 1.0 },
    { ruleId: 'DEBT_TO_INCOME_LATEST', signal: 'GREEN', ratioLatest: 2.0, thresholdGreen: 4.0, thresholdYellow: 8.0, netIncomePositive: true },
    { ruleId: 'CAPEX_INTENSITY_10Y_AVG', signal: 'GREEN', averagePercent: 18.0, thresholdGreenPercent: 25, thresholdYellowPercent: 50 },
  ];

  it.each(ALL_EP021_GREEN)('$ruleId GREEN: title, subtitle e tooltip non vuoti', (s) => {
    const { title, subtitle, tooltip } = formatRuleSignal(s);
    expect(title.length, `title vuoto per ${s.ruleId}`).toBeGreaterThan(0);
    expect(subtitle.length, `subtitle vuoto per ${s.ruleId}`).toBeGreaterThan(0);
    expect(tooltip.length, `tooltip vuoto per ${s.ruleId}`).toBeGreaterThan(0);
  });
});
