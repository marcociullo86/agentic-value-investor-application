import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import {
  TrafficLightPanel,
  BUFFETT_QUALITY_RULES,
  GRAHAM_DEFENSIVE_RULES,
} from './TrafficLightPanel';
import type { RuleSignal } from '@/lib/api/analysis';

/**
 * Test TrafficLightPanel — TSK-021 DoD (US-014) + TSK-088 DoD (US-032 / EP-010).
 *
 * Copre:
 *  - Render con i 7 ruleId Buffett canonici → sezione "Criteri Buffett Quality"
 *    presente, 7 cards, counter header invariato.
 *  - Render con i 6 ruleId Graham canonici → sezione "Criteri Graham Defensive"
 *    presente, 6 cards.
 *  - Render con tutti i 13 ruleId → entrambe le sezioni visibili + counter
 *    aggregato somma 13.
 *  - Fallback: ruleId fuori dalle 2 liste → sezione "Altri criteri".
 *  - Sort lessicografico per ruleId all'interno di ciascuna sezione.
 *  - Counter header conta GREEN/YELLOW/RED/INDETERMINATE/NOT_CALCULABLE.
 *  - Empty state quando signals=[].
 *  - Accessibility: h3 per ogni sezione visibile.
 *  - Sezione vuota OMESSA (no rendering di sezione senza signal).
 */

function makeSignal(ruleId: string, signal: RuleSignal['signal']): RuleSignal {
  return {
    ruleId,
    signal,
    observedValue: signal === 'NOT_CALCULABLE' ? null : 0.18,
    threshold: 'threshold-text',
    rationale: 'rationale-text',
  };
}

// Helpers — i 7 Buffett + 6 Graham canonici da OpenAPI (TSK-087).
const BUFFETT_IDS: ReadonlyArray<string> = Array.from(BUFFETT_QUALITY_RULES);
const GRAHAM_IDS: ReadonlyArray<string> = Array.from(GRAHAM_DEFENSIVE_RULES);

describe('TrafficLightPanel', () => {
  it('Test 1 — empty signals → empty message + role=status', () => {
    render(<TrafficLightPanel signals={[]} />);
    const empty = screen.getByTestId('traffic-light-panel-empty');
    expect(empty).toBeInTheDocument();
    expect(empty).toHaveAttribute('role', 'status');
    expect(empty).toHaveTextContent(/nessuna regola valutata/i);
    expect(screen.queryByTestId('traffic-light-panel')).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('traffic-light-section-buffett'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('traffic-light-section-graham'),
    ).not.toBeInTheDocument();
  });

  it('Test 2 — aria-label sul section', () => {
    render(<TrafficLightPanel signals={[makeSignal('ROE_10Y_AVG', 'GREEN')]} />);
    const section = screen.getByTestId('traffic-light-panel');
    expect(section).toHaveAttribute(
      'aria-label',
      'Pannello Traffic Light delle regole del Rule Engine',
    );
  });

  it('Test 3 — renders 7 Buffett rules section when 7 Buffett signals provided', () => {
    const signals: ReadonlyArray<RuleSignal> = BUFFETT_IDS.map((id) =>
      makeSignal(id, 'GREEN'),
    );
    render(<TrafficLightPanel signals={signals} />);

    const buffettSection = screen.getByTestId('traffic-light-section-buffett');
    expect(buffettSection).toBeInTheDocument();
    const buffettGrid = screen.getByTestId('traffic-light-section-buffett-grid');
    expect(buffettGrid.children).toHaveLength(7);

    // No Graham section, no fallback section
    expect(
      screen.queryByTestId('traffic-light-section-graham'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('traffic-light-section-other'),
    ).not.toBeInTheDocument();

    // Heading h3 visibile
    const heading = screen.getByTestId('traffic-light-section-buffett-heading');
    expect(heading.tagName).toBe('H3');
    expect(heading).toHaveTextContent('Criteri Buffett Quality');

    // Counter aggregato somma 7
    expect(
      screen.getByTestId('traffic-light-counter-GREEN'),
    ).toHaveTextContent('7 OK');
  });

  it('Test 4 — renders 6 Graham rules section when 6 Graham signals provided', () => {
    const signals: ReadonlyArray<RuleSignal> = GRAHAM_IDS.map((id) =>
      makeSignal(id, 'GREEN'),
    );
    render(<TrafficLightPanel signals={signals} />);

    const grahamSection = screen.getByTestId('traffic-light-section-graham');
    expect(grahamSection).toBeInTheDocument();
    const grahamGrid = screen.getByTestId('traffic-light-section-graham-grid');
    expect(grahamGrid.children).toHaveLength(6);

    // No Buffett section, no fallback section
    expect(
      screen.queryByTestId('traffic-light-section-buffett'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('traffic-light-section-other'),
    ).not.toBeInTheDocument();

    // Heading h3 visibile
    const heading = screen.getByTestId('traffic-light-section-graham-heading');
    expect(heading.tagName).toBe('H3');
    expect(heading).toHaveTextContent('Criteri Graham Defensive');

    // Counter aggregato somma 6
    expect(
      screen.getByTestId('traffic-light-counter-GREEN'),
    ).toHaveTextContent('6 OK');
  });

  it('Test 5 — renders both sections when 13 signals provided (mix GREEN/YELLOW/RED/INDETERMINATE)', () => {
    // Mix: alterna Signal per coprire counter aggregato multi-stato.
    const signalStates: readonly [
      RuleSignal['signal'],
      RuleSignal['signal'],
      RuleSignal['signal'],
      RuleSignal['signal'],
    ] = ['GREEN', 'YELLOW', 'RED', 'INDETERMINATE'];
    const pickSignal = (idx: number): RuleSignal['signal'] =>
      signalStates[idx % signalStates.length] as RuleSignal['signal'];
    const all: ReadonlyArray<RuleSignal> = [...BUFFETT_IDS, ...GRAHAM_IDS].map(
      (id, idx) => makeSignal(id, pickSignal(idx)),
    );
    render(<TrafficLightPanel signals={all} />);

    // Entrambe le sezioni rese
    expect(
      screen.getByTestId('traffic-light-section-buffett'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('traffic-light-section-graham'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('traffic-light-section-other'),
    ).not.toBeInTheDocument();

    // 7+6 cards
    expect(
      screen.getByTestId('traffic-light-section-buffett-grid').children,
    ).toHaveLength(7);
    expect(
      screen.getByTestId('traffic-light-section-graham-grid').children,
    ).toHaveLength(6);

    // Header counter aggregato = 13: 4 GREEN + 3 YELLOW + 3 RED + 3 INDETERMINATE
    // (13 mod 4 → idx 0,4,8,12=GREEN; 1,5,9=YELLOW; 2,6,10=RED; 3,7,11=INDETERMINATE)
    expect(
      screen.getByTestId('traffic-light-counter-GREEN'),
    ).toHaveTextContent('4 OK');
    expect(
      screen.getByTestId('traffic-light-counter-YELLOW'),
    ).toHaveTextContent('3 Attenzione');
    expect(
      screen.getByTestId('traffic-light-counter-RED'),
    ).toHaveTextContent('3 Non soddisfatta');
    expect(
      screen.getByTestId('traffic-light-counter-INDETERMINATE'),
    ).toHaveTextContent('3 Indeterminato');

    // Le 2 h3 esistono e sono identificabili
    const buffettH3 = screen.getByTestId('traffic-light-section-buffett-heading');
    const grahamH3 = screen.getByTestId('traffic-light-section-graham-heading');
    expect(buffettH3.tagName).toBe('H3');
    expect(grahamH3.tagName).toBe('H3');
  });

  it('Test 6 — renders unknown ruleId in fallback "Altri criteri" section', () => {
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal('ROE_10Y_AVG', 'GREEN'), // Buffett known
      makeSignal('SIZE_LATEST', 'GREEN'), // Graham known
      makeSignal('FUTURE_RULE_XYZ', 'YELLOW'), // unknown → fallback
      makeSignal('ANOTHER_NEW_RULE', 'GREEN'), // unknown → fallback
    ];
    render(<TrafficLightPanel signals={signals} />);

    const otherSection = screen.getByTestId('traffic-light-section-other');
    expect(otherSection).toBeInTheDocument();
    const otherGrid = screen.getByTestId('traffic-light-section-other-grid');
    expect(otherGrid.children).toHaveLength(2);

    const otherHeading = screen.getByTestId('traffic-light-section-other-heading');
    expect(otherHeading.tagName).toBe('H3');
    expect(otherHeading).toHaveTextContent('Altri criteri');

    // Card unknown render correttamente con RuleSignalCard
    expect(
      within(otherGrid).getByTestId('rule-signal-card-FUTURE_RULE_XYZ'),
    ).toBeInTheDocument();
    expect(
      within(otherGrid).getByTestId('rule-signal-card-ANOTHER_NEW_RULE'),
    ).toBeInTheDocument();

    // Le sezioni Buffett e Graham coesistono
    expect(
      screen.getByTestId('traffic-light-section-buffett-grid').children,
    ).toHaveLength(1);
    expect(
      screen.getByTestId('traffic-light-section-graham-grid').children,
    ).toHaveLength(1);

    // Counter aggregato somma 4
    expect(
      screen.getByTestId('traffic-light-counter-GREEN'),
    ).toHaveTextContent('3 OK');
    expect(
      screen.getByTestId('traffic-light-counter-YELLOW'),
    ).toHaveTextContent('1 Attenzione');
  });

  it('Test 7 — sort lessicografico ascending per ruleId dentro ciascuna sezione', () => {
    // Inserisco i Buffett in ordine NON ordinato e mi aspetto sort lessico.
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal('ROIC_10Y_AVG', 'GREEN'),
      makeSignal('CAPEX_INTENSITY_10Y_AVG', 'GREEN'),
      makeSignal('ROE_10Y_AVG', 'GREEN'),
      makeSignal('SIZE_LATEST', 'GREEN'),
      makeSignal('EPS_GROWTH_10Y', 'GREEN'),
      makeSignal('PE_3Y_AVG', 'GREEN'),
    ];
    render(<TrafficLightPanel signals={signals} />);

    const buffettGrid = screen.getByTestId('traffic-light-section-buffett-grid');
    const buffettCards = within(buffettGrid)
      .getAllByTestId(/^rule-signal-card-/)
      .map((el) => el.getAttribute('data-testid'));
    expect(buffettCards).toEqual([
      'rule-signal-card-CAPEX_INTENSITY_10Y_AVG',
      'rule-signal-card-ROE_10Y_AVG',
      'rule-signal-card-ROIC_10Y_AVG',
    ]);

    const grahamGrid = screen.getByTestId('traffic-light-section-graham-grid');
    const grahamCards = within(grahamGrid)
      .getAllByTestId(/^rule-signal-card-/)
      .map((el) => el.getAttribute('data-testid'));
    expect(grahamCards).toEqual([
      'rule-signal-card-EPS_GROWTH_10Y',
      'rule-signal-card-PE_3Y_AVG',
      'rule-signal-card-SIZE_LATEST',
    ]);
  });

  it('Test 8 — counter omette stati con count=0', () => {
    const signals: ReadonlyArray<RuleSignal> = [
      makeSignal('ROE_10Y_AVG', 'GREEN'),
      makeSignal('ROIC_10Y_AVG', 'GREEN'),
    ];
    render(<TrafficLightPanel signals={signals} />);
    expect(screen.getByTestId('traffic-light-counter-GREEN')).toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-counter-YELLOW')).not.toBeInTheDocument();
    expect(screen.queryByTestId('traffic-light-counter-RED')).not.toBeInTheDocument();
  });

  it('Test 9 — counter aggregato corretto su tutti 13 ruleId', () => {
    // 13 GREEN puri → counter = "13 OK", nessun altro counter visibile.
    const signals: ReadonlyArray<RuleSignal> = [...BUFFETT_IDS, ...GRAHAM_IDS].map(
      (id) => makeSignal(id, 'GREEN'),
    );
    render(<TrafficLightPanel signals={signals} />);
    expect(
      screen.getByTestId('traffic-light-counter-GREEN'),
    ).toHaveTextContent('13 OK');
    expect(
      screen.queryByTestId('traffic-light-counter-YELLOW'),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByTestId('traffic-light-counter-RED'),
    ).not.toBeInTheDocument();
  });

  it('Test 10 — structural integrity of 13 ruleId mix Signal render', () => {
    const signalStates: readonly [
      RuleSignal['signal'],
      RuleSignal['signal'],
      RuleSignal['signal'],
      RuleSignal['signal'],
    ] = ['GREEN', 'YELLOW', 'RED', 'INDETERMINATE'];
    const pickSignal = (idx: number): RuleSignal['signal'] =>
      signalStates[idx % signalStates.length] as RuleSignal['signal'];
    const signals: ReadonlyArray<RuleSignal> = [
      ...BUFFETT_IDS,
      ...GRAHAM_IDS,
    ].map((id, idx) => makeSignal(id, pickSignal(idx)));
    const { container } = render(<TrafficLightPanel signals={signals} />);
    expect(container.firstChild).toBeTruthy();
    expect(
      screen.getByTestId('traffic-light-section-buffett-grid').children,
    ).toHaveLength(7);
    expect(
      screen.getByTestId('traffic-light-section-graham-grid').children,
    ).toHaveLength(6);
  });
});
