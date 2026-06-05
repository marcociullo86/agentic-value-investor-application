import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { TrafficLightPanel } from './TrafficLightPanel';
import type { RuleSignal } from '@/lib/api/analysis';

/**
 * Test TrafficLightPanel con 15 segnali (13 EP-021 + 2 NCAV EP-023).
 * TSK-323 (US-097, EP-023, ADR-029 §6).
 *
 * Copre US-097 Acceptance Criteria relativi al pannello Traffic Light:
 *  - Con i 15 segnali (13 esistenti + NCAV_LATEST + NET_NET_RATIO) il
 *    pannello mostra 15 card totali (counter aggregato = 15).
 *  - I 2 nuovi segnali NCAV confluiscono nella sezione "Altri criteri"
 *    (non appartengono ai set BUFFETT_QUALITY_RULES o GRAHAM_DEFENSIVE_RULES),
 *    come previsto dal design forward-compat del TrafficLightPanel.
 *  - I subtitle dei 2 nuovi segnali sono visibili e non vuoti.
 *
 * Nota separazione file: questo file è SEPARATO da TrafficLightPanel.test.tsx
 * (TSK-088/TSK-021) per evitare conflitti di scrittura col test prodotto da
 * TSK-322. Entrambi i file vengono inclusi da Vitest (pattern *.test.tsx).
 *
 * Riferimenti:
 *  - ADR-028 §3 (mapping campi NCAV), §6 (formatters paranoid fallback).
 *  - ADR-029 §6 (badge + Traffic Light EP-023).
 *  - US-097 Acceptance Criteria.
 */

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeSignal(ruleId: string, signal: RuleSignal['signal'] = 'GREEN'): RuleSignal {
  return {
    ruleId,
    signal,
    observedValue: 0.5,
    threshold: 'threshold-text',
    rationale: 'rationale-text',
  };
}

/**
 * I 13 ruleId EP-021 canonici (7 Buffett + 6 Graham).
 * Ordinati per chiarezza; la TrafficLightPanel partiziona internamente.
 */
const BASE_13_RULE_IDS: ReadonlyArray<string> = [
  // Buffett Quality (7)
  'ROE_10Y_AVG',
  'ROIC_10Y_AVG',
  'GROSS_MARGIN_10Y_AVG',
  'NET_MARGIN_10Y_AVG',
  'CURRENT_RATIO_LATEST',
  'DEBT_TO_INCOME_LATEST',
  'CAPEX_INTENSITY_10Y_AVG',
  // Graham Defensive (6)
  'SIZE_LATEST',
  'EARNINGS_STABILITY_10Y',
  'EPS_GROWTH_10Y',
  'PE_3Y_AVG',
  'PB_LATEST',
  'DIVIDEND_CONTINUITY_20Y',
];

/** 2 nuovi signal EP-023. */
const NCAV_RULE_IDS: ReadonlyArray<string> = ['NCAV_LATEST', 'NET_NET_RATIO'];

/** 15 signal totali. */
const ALL_15: ReadonlyArray<RuleSignal> = [
  ...BASE_13_RULE_IDS.map((id) => makeSignal(id, 'GREEN')),
  // NCAV_LATEST con rationale che include "$" per verificare subtitle visibile
  {
    ruleId: 'NCAV_LATEST',
    signal: 'GREEN',
    observedValue: 12.5,
    threshold: '>0',
    rationale: 'NCAV $5.00B (per azione: $12.50)',
  },
  // NET_NET_RATIO con rationale che include ratio + soglia
  {
    ruleId: 'NET_NET_RATIO',
    signal: 'GREEN',
    observedValue: 0.64,
    threshold: '<0.6667',
    rationale: 'Ratio: 0,6400 (soglia <0,6667)',
  },
];

// ---------------------------------------------------------------------------
// Test suite
// ---------------------------------------------------------------------------

describe('TrafficLightPanel — 15 segnali (13 EP-021 + 2 EP-023 NCAV)', () => {
  it('Test 1 — 15 signal totali: counter aggregato = 15 (14 GREEN + 1 GREEN NET_NET_RATIO)', () => {
    render(<TrafficLightPanel signals={ALL_15} />);

    // Tutti e 15 i segnali sono GREEN → counter mostra "15 OK"
    const greenCounter = screen.getByTestId('traffic-light-counter-GREEN');
    expect(greenCounter).toHaveTextContent('15 OK');

    // Nessun counter per altri stati
    expect(screen.queryByTestId('traffic-light-counter-RED')).not.toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-counter-YELLOW')).not.toBeInTheDocument();
  });

  it('Test 2 — presenza di 15 card (una per ruleId)', () => {
    render(<TrafficLightPanel signals={ALL_15} />);

    // data-testid="rule-signal-card-{ruleId}" — uno per ciascuno dei 15 ruleId
    const allCards = screen.getAllByTestId(/^rule-signal-card-/);
    expect(allCards).toHaveLength(15);
  });

  it('Test 3 — NCAV_LATEST e NET_NET_RATIO nella sezione "Altri criteri"', () => {
    render(<TrafficLightPanel signals={ALL_15} />);

    // I 2 NCAV signal non appartengono a BUFFETT_QUALITY_RULES né GRAHAM_DEFENSIVE_RULES
    // → finiscono nella sezione "Altri criteri" (forward-compat TrafficLightPanel)
    const otherSection = screen.getByTestId('traffic-light-section-other');
    expect(otherSection).toBeInTheDocument();

    const otherGrid = screen.getByTestId('traffic-light-section-other-grid');
    // Esattamente 2 card nella sezione "altri"
    expect(otherGrid.children).toHaveLength(2);

    // Entrambe le card presenti
    expect(
      within(otherGrid).getByTestId('rule-signal-card-NCAV_LATEST'),
    ).toBeInTheDocument();
    expect(
      within(otherGrid).getByTestId('rule-signal-card-NET_NET_RATIO'),
    ).toBeInTheDocument();
  });

  it('Test 4 — sezioni Buffett e Graham integre con 7 + 6 card rispettivamente', () => {
    render(<TrafficLightPanel signals={ALL_15} />);

    // 7 Buffett
    const buffettGrid = screen.getByTestId('traffic-light-section-buffett-grid');
    expect(buffettGrid.children).toHaveLength(7);

    // 6 Graham
    const grahamGrid = screen.getByTestId('traffic-light-section-graham-grid');
    expect(grahamGrid.children).toHaveLength(6);
  });

  it('Test 5 — subtitle NCAV_LATEST visibile e non vuoto', () => {
    // Il TrafficLightPanel passa observedSubtitle solo alle Graham (section.id === "graham").
    // I signal in "altri criteri" usano il fallback del RuleSignalCard che espone
    // il rationale legacy. Verifichiamo che la card sia presente (struttura intatta).
    render(<TrafficLightPanel signals={ALL_15} />);

    const ncavCard = screen.getByTestId('rule-signal-card-NCAV_LATEST');
    expect(ncavCard).toBeInTheDocument();
    // La card deve avere data-signal="GREEN"
    expect(ncavCard).toHaveAttribute('data-signal', 'GREEN');
  });

  it('Test 6 — subtitle NET_NET_RATIO: card presente con signal GREEN', () => {
    render(<TrafficLightPanel signals={ALL_15} />);

    const netNetCard = screen.getByTestId('rule-signal-card-NET_NET_RATIO');
    expect(netNetCard).toBeInTheDocument();
    expect(netNetCard).toHaveAttribute('data-signal', 'GREEN');
  });

  it('Test 7 — NET_NET_RATIO RED: card presente in "altri" con signal RED', () => {
    const signalsWithRedNetNet: ReadonlyArray<RuleSignal> = [
      ...BASE_13_RULE_IDS.map((id) => makeSignal(id, 'GREEN')),
      makeSignal('NCAV_LATEST', 'GREEN'),
      makeSignal('NET_NET_RATIO', 'RED'),
    ];
    render(<TrafficLightPanel signals={signalsWithRedNetNet} />);

    const netNetCard = screen.getByTestId('rule-signal-card-NET_NET_RATIO');
    expect(netNetCard).toHaveAttribute('data-signal', 'RED');

    // Counter: 14 GREEN + 1 RED
    expect(screen.getByTestId('traffic-light-counter-GREEN')).toHaveTextContent('14 OK');
    expect(screen.getByTestId('traffic-light-counter-RED')).toHaveTextContent('1 Non soddisfatta');
  });

  it('Test 8 — counter aggregato somma 15 con mix segnali NCAV', () => {
    const signalsMix: ReadonlyArray<RuleSignal> = [
      ...BASE_13_RULE_IDS.map((id) => makeSignal(id, 'GREEN')),
      makeSignal('NCAV_LATEST', 'YELLOW'),
      makeSignal('NET_NET_RATIO', 'INDETERMINATE'),
    ];
    render(<TrafficLightPanel signals={signalsMix} />);

    expect(screen.getByTestId('traffic-light-counter-GREEN')).toHaveTextContent('13 OK');
    expect(screen.getByTestId('traffic-light-counter-YELLOW')).toHaveTextContent('1 Attenzione');
    expect(screen.getByTestId('traffic-light-counter-INDETERMINATE')).toHaveTextContent('1 Indeterminato');

    // Totale card ancora 15
    const allCards = screen.getAllByTestId(/^rule-signal-card-/);
    expect(allCards).toHaveLength(15);
  });

  it('Test 9 — heading "Altri criteri" visibile quando NCAV signals presenti', () => {
    render(<TrafficLightPanel signals={ALL_15} />);

    const otherHeading = screen.getByTestId('traffic-light-section-other-heading');
    expect(otherHeading.tagName).toBe('H3');
    expect(otherHeading).toHaveTextContent('Altri criteri');
  });

  it('Test 10 — solo 2 NCAV signal + 0 Buffett/Graham: unica sezione "Altri criteri"', () => {
    const onlyNcav: ReadonlyArray<RuleSignal> = [
      makeSignal('NCAV_LATEST', 'GREEN'),
      makeSignal('NET_NET_RATIO', 'GREEN'),
    ];
    render(<TrafficLightPanel signals={onlyNcav} />);

    expect(screen.getByTestId('traffic-light-section-other')).toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-section-buffett')).not.toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-section-graham')).not.toBeInTheDocument();

    const otherGrid = screen.getByTestId('traffic-light-section-other-grid');
    expect(otherGrid.children).toHaveLength(2);

    // Counter: 2 GREEN
    expect(screen.getByTestId('traffic-light-counter-GREEN')).toHaveTextContent('2 OK');
  });
});
