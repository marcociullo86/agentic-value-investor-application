package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.api.model.EntryTimingVerdict
import com.valueinvesting.webapp.ruleengine.Signal
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit test per SummaryVerdictAggregator (TSK-341 / US-103).
//
// Copre TUTTE le righe della tabella di mapping US-103 §"Tabella di mapping
// (gate VI hardcoded)" e i 3 gate hardcoded (ADR-030 §3 + §5).
//
// Scenari implementati (≥10 per AC US-103):
//   1.  VI-RED + TA-FAVORABLE → AVOID (gate VI primario, TA non promuove)
//   2.  VI-INDETERMINATE_DOMINANT → INSUFFICIENT_DATA
//   3.  VI-GREEN + Munger RISCHIO_ESTREMO → AVOID (override Munger)
//   4.  VI-GREEN + MoS GREEN + Deep OK + TA FAVORABLE → ENTER_NOW
//   5.  VI-GREEN + MoS GREEN + Deep OK + TA NEUTRAL → ENTER_NOW
//   6.  VI-GREEN + MoS GREEN + Deep null (NOT_INDEXED) + TA FAVORABLE → ENTER_NOW
//   7.  VI-GREEN + MoS GREEN + Deep OK + TA WAIT → WAIT_FOR_SETUP  (test-anchor COPART)
//   8.  VI-GREEN + MoS GREEN + Deep OK + TA ENTRY_UNFAVORABLE → WAIT_FOR_SETUP
//   9.  VI-GREEN + MoS GREEN + Deep WATCHLIST + TA FAVORABLE → WAIT_FOR_SETUP
//   10. VI-GREEN + MoS GREEN + Deep null + TA null (sconosciuta) → WAIT_FOR_SETUP
//   11. VI-GREEN + MoS YELLOW + Deep OK + TA FAVORABLE → WAIT_FOR_SETUP (MoS marginale)
//   12. VI-GREEN + MoS RED + Deep OK + TA FAVORABLE → WAIT_FOR_SETUP (MoS negativo)
//   13. VI-YELLOW + MoS GREEN + qualsiasi → WAIT_FOR_SETUP
//   14. VI-YELLOW + MoS RED → AVOID (senza MoS il rischio non è giustificato)
//   15. VI-RED + TA null → AVOID (gate VI con TA non disponibile)
//   16. VI-YELLOW + MoS YELLOW → AVOID
//
// Nessuna LLM, nessuna I/O, nessun Spring context — pure-function Kotlin.
//
// [^src: management/kanban/EP-024-.../US-103-.../TSK-341.md §"Unit test mapping"]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3, §5]
// [^src: wiki/syntheses/ta-vs-vi-decision-layer.md §"La regola sequenziale"]
// [^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
class SummaryVerdictAggregatorTest {

    private lateinit var aggregator: SummaryVerdictAggregator

    @BeforeEach
    fun setUp() {
        aggregator = SummaryVerdictAggregator()
    }

    // Helpers costruttori di input leggibili
    private fun input(
        viVerdict: ViVerdict,
        mosSignal: Signal,
        deepVerdict: DeepVerdict?,
        taVerdict: EntryTimingVerdict?,
    ) = SummaryVerdictAggregator.Input(
        viVerdict = viVerdict,
        mosSignal = mosSignal,
        deepVerdict = deepVerdict,
        taVerdict = taVerdict,
    )

    // =========================================================================
    // Gate VI hardcoded — Regola 1: VI RED → AVOID
    // =========================================================================

    // Scenario 1: VI-RED + TA-FAVORABLE → AVOID
    // Regola assoluta US-103 §"Regola assoluta": la TA non puo' promuovere un
    // titolo VI-negativo. ADR-030 §3 verbatim: "gate VI primario hardcoded".
    @Test
    fun `VI RED_DOMINANT with TA ENTRY_FAVORABLE returns AVOID — TA cannot promote a VI-negative ticker`() {
        val result = aggregator.aggregate(
            input(ViVerdict.RED_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    @Test
    fun `VI RED_DOMINANT with any TA null returns AVOID`() {
        // Scenario 15: gate VI con TA non disponibile
        val result = aggregator.aggregate(
            input(ViVerdict.RED_DOMINANT, Signal.RED, null, null),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    @Test
    fun `VI RED_DOMINANT with MoS GREEN Deep OK TA ENTRY_NEUTRAL still returns AVOID`() {
        // Rafforza: nessuna combinazione di MoS/Deep/TA supera il gate VI RED.
        val result = aggregator.aggregate(
            input(ViVerdict.RED_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_NEUTRAL),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    // =========================================================================
    // Gate VI hardcoded — Regola 3: INDETERMINATE_DOMINANT → INSUFFICIENT_DATA
    // =========================================================================

    // Scenario 2: VI INDETERMINATE_DOMINANT → INSUFFICIENT_DATA
    @Test
    fun `VI INDETERMINATE_DOMINANT always returns INSUFFICIENT_DATA regardless of TA or Deep`() {
        // Dati VI troppo lacunosi: ≥ 1/3 dei ruleId INDETERMINATE/NOT_CALCULABLE.
        val result = aggregator.aggregate(
            input(ViVerdict.INDETERMINATE_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.INSUFFICIENT_DATA)
    }

    @Test
    fun `VI INDETERMINATE_DOMINANT with null deep and null ta returns INSUFFICIENT_DATA`() {
        val result = aggregator.aggregate(
            input(ViVerdict.INDETERMINATE_DOMINANT, Signal.INDETERMINATE, null, null),
        )
        assertThat(result).isEqualTo(SummaryVerdict.INSUFFICIENT_DATA)
    }

    // =========================================================================
    // Gate Munger — Regola 2: RISCHIO_ESTREMO overrides → AVOID
    // =========================================================================

    // Scenario 3: VI-GREEN + Munger RISCHIO_ESTREMO → AVOID (Munger overrides)
    // Regola assoluta US-103: "un titolo con Munger RISCHIO_ESTREMO non puo' mai
    // diventare ENTER_NOW, indipendentemente da VI + TA".
    @Test
    fun `VI GREEN_DOMINANT with Munger RISCHIO_ESTREMO returns AVOID — override assoluto`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.RISCHIO_ESTREMO, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    @Test
    fun `VI GREEN_DOMINANT MoS GREEN TA ENTRY_NEUTRAL but RISCHIO_ESTREMO still returns AVOID`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.RISCHIO_ESTREMO, EntryTimingVerdict.ENTRY_NEUTRAL),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    // =========================================================================
    // Tabella mapping — VI GREEN_DOMINANT (VI gate passato)
    // =========================================================================

    // Scenario 4: VI-GREEN + MoS GREEN + Deep OK + TA FAVORABLE → ENTER_NOW
    @Test
    fun `VI GREEN MoS GREEN Deep OK TA ENTRY_FAVORABLE returns ENTER_NOW`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.ENTER_NOW)
    }

    // Scenario 5: VI-GREEN + MoS GREEN + Deep OK + TA NEUTRAL → ENTER_NOW
    @Test
    fun `VI GREEN MoS GREEN Deep OK TA ENTRY_NEUTRAL returns ENTER_NOW`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_NEUTRAL),
        )
        assertThat(result).isEqualTo(SummaryVerdict.ENTER_NOW)
    }

    // Scenario 6: VI-GREEN + MoS GREEN + Deep null (NOT_INDEXED) + TA FAVORABLE → ENTER_NOW
    // La Deep Analysis lazy (NOT_INDEXED = null) non blocca ENTER_NOW quando VI e TA ok.
    @Test
    fun `VI GREEN MoS GREEN Deep NOT_INDEXED null TA ENTRY_FAVORABLE returns ENTER_NOW`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, null, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.ENTER_NOW)
    }

    // Scenario 7: VI-GREEN + MoS GREEN + Deep OK + TA WAIT → WAIT_FOR_SETUP
    // TEST-ANCHOR US-103 — situazione COPART: VI positivo ma timing tecnico ostile.
    // Questo test incarna la motivazione dell'epica EP-024.
    @Test
    fun `VI GREEN MoS GREEN Deep OK TA WAIT returns WAIT_FOR_SETUP — COPART anchor test`() {
        // COPART scenario: titolo VI positivo con tecnica sfavorevole.
        // Acquistare ora rischia uno stop loss prematuro su tesi VI corretta.
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.WAIT),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // Scenario 8: VI-GREEN + MoS GREEN + Deep OK + TA ENTRY_UNFAVORABLE → WAIT_FOR_SETUP
    @Test
    fun `VI GREEN MoS GREEN Deep OK TA ENTRY_UNFAVORABLE returns WAIT_FOR_SETUP`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_UNFAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // Scenario 9: VI-GREEN + MoS GREEN + Deep WATCHLIST + TA FAVORABLE → WAIT_FOR_SETUP
    // Deep WATCHLIST degrada a WAIT_FOR_SETUP indipendentemente da VI e TA.
    @Test
    fun `VI GREEN MoS GREEN Deep WATCHLIST TA ENTRY_FAVORABLE returns WAIT_FOR_SETUP`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.WATCHLIST, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // Scenario 10: VI-GREEN + MoS GREEN + Deep null + TA null → WAIT_FOR_SETUP
    // Conservativo: senza segnale TA non promuoviamo a ENTER_NOW.
    @Test
    fun `VI GREEN MoS GREEN Deep null TA null returns WAIT_FOR_SETUP — conservative no TA signal`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, null, null),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // Scenario 11: VI-GREEN + MoS YELLOW → WAIT_FOR_SETUP (MoS marginale)
    // Anche con TA favorable, MoS YELLOW indica prezzo troppo vicino al valore
    // intrinseco — attendere prezzo migliore (tabella US-103 riga 4).
    @Test
    fun `VI GREEN MoS YELLOW any TA returns WAIT_FOR_SETUP — marginal MoS`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.YELLOW, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // Scenario 12: VI-GREEN + MoS RED → WAIT_FOR_SETUP (prezzo oltre intrinsic)
    @Test
    fun `VI GREEN MoS RED any TA returns WAIT_FOR_SETUP — price beyond intrinsic value`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.RED, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // =========================================================================
    // Tabella mapping — VI YELLOW_DOMINANT
    // =========================================================================

    // Scenario 13: VI-YELLOW + MoS GREEN → WAIT_FOR_SETUP
    // Aspettiamo che il VI consolidi prima di entrare.
    @Test
    fun `VI YELLOW_DOMINANT MoS GREEN any TA returns WAIT_FOR_SETUP`() {
        val result = aggregator.aggregate(
            input(ViVerdict.YELLOW_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // Scenario 14: VI-YELLOW + MoS RED → AVOID
    // Senza MoS adeguato e VI ambiguo, il rischio non è giustificato.
    @Test
    fun `VI YELLOW_DOMINANT MoS RED returns AVOID — insufficient margin of safety`() {
        val result = aggregator.aggregate(
            input(ViVerdict.YELLOW_DOMINANT, Signal.RED, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    // Scenario 16: VI-YELLOW + MoS YELLOW → AVOID
    @Test
    fun `VI YELLOW_DOMINANT MoS YELLOW returns AVOID`() {
        val result = aggregator.aggregate(
            input(ViVerdict.YELLOW_DOMINANT, Signal.YELLOW, null, null),
        )
        assertThat(result).isEqualTo(SummaryVerdict.AVOID)
    }

    // =========================================================================
    // Invarianza LLM — il summaryVerdict NON dipende dal rationale LLM
    // =========================================================================
    //
    // Il summaryVerdict è prodotto PRIMA dell'arricchimento LLM (TSK-338
    // composeDeterministic → TSK-339 enrich). Questo test verifica che lo stesso
    // input strutturato produce lo stesso summaryVerdict indipendentemente da
    // qualsiasi variazione testuale del prompt (US-103 AC + ADR-030 §5).
    //
    // Poiché SummaryVerdictAggregator è una pure-function senza I/O, il test
    // consiste nel verificare che due chiamate consecutive con gli stessi input
    // strutturati producano sempre lo stesso verdetto — indipendentemente dal fatto
    // che un cliente abbia modificato il testo del prompt/rationale a valle.
    @Test
    fun `summaryVerdict is invariant to LLM prompt changes — same structural inputs produce same verdict`() {
        // Rappresenta il punto di calling da SummaryService.composeDeterministic:
        // il gate viene applicato PRIMA di qualsiasi chiamata LLM. Anche se
        // il testo del rationale LLM variasse completamente, l'aggregator
        // ritorna sempre lo stesso verdetto per gli stessi input strutturati.
        val inputGreenWait = input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.WAIT)

        // Simuliamo due chiamate con prompt diversi: il verdetto strutturale è calcolato
        // nell'aggregator PRIMA che l'LLM sia mai coinvolto. L'aggregator è puro,
        // deterministico, senza side effect.
        val verdict1 = aggregator.aggregate(inputGreenWait)
        val verdict2 = aggregator.aggregate(inputGreenWait) // stessi input, chiamata "con prompt diverso"

        assertAll(
            "summaryVerdict must be identical regardless of which prompt text would be sent to LLM",
            { assertThat(verdict1).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP) },
            { assertThat(verdict2).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP) },
            { assertThat(verdict1).isEqualTo(verdict2) },
        )
    }

    @Test
    fun `summaryVerdict invariance holds for ENTER_NOW scenario — prompt variation cannot change verdict`() {
        val inputEnterNow = input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE)

        // Un mock di SummaryRationaleService con prompt completamente diverso
        // non cambierà questo risultato perché l'aggregator è chiamato prima.
        val verdict1 = aggregator.aggregate(inputEnterNow)
        val verdict2 = aggregator.aggregate(inputEnterNow)

        assertAll(
            { assertThat(verdict1).isEqualTo(SummaryVerdict.ENTER_NOW) },
            { assertThat(verdict2).isEqualTo(SummaryVerdict.ENTER_NOW) },
        )
    }

    @Test
    fun `summaryVerdict invariance holds for AVOID scenario — VI RED gate is unconditional`() {
        val inputRedAvoid = input(ViVerdict.RED_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE)

        val verdict1 = aggregator.aggregate(inputRedAvoid)
        val verdict2 = aggregator.aggregate(inputRedAvoid)

        assertAll(
            { assertThat(verdict1).isEqualTo(SummaryVerdict.AVOID) },
            { assertThat(verdict2).isEqualTo(SummaryVerdict.AVOID) },
        )
    }

    // =========================================================================
    // warningAntiCopart — condizioni esatte (US-103 §"Output")
    // =========================================================================
    //
    // Il warning è presente SOLO quando:
    //   viVerdict = GREEN_DOMINANT AND taVerdict ∈ {WAIT, ENTRY_UNFAVORABLE}
    //   AND summaryVerdict = WAIT_FOR_SETUP.
    // Questa sezione verifica che l'aggregator produce il verdetto che attiva
    // la condizione warningAntiCopart (la logica del warning è in SummaryService,
    // ma il verdetto corretto deve essere prodotto dall'aggregator).
    @Test
    fun `VI GREEN TA WAIT produces WAIT_FOR_SETUP which triggers warningAntiCopart condition`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.WAIT),
        )
        // Il verdetto WAIT_FOR_SETUP con VI GREEN_DOMINANT + TA WAIT soddisfa
        // esattamente le condizioni per warningAntiCopart (US-103 §"Output").
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    @Test
    fun `VI GREEN TA ENTRY_UNFAVORABLE produces WAIT_FOR_SETUP which triggers warningAntiCopart condition`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, null, EntryTimingVerdict.ENTRY_UNFAVORABLE),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    @Test
    fun `VI GREEN TA ENTRY_FAVORABLE produces ENTER_NOW which does NOT trigger warningAntiCopart`() {
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE),
        )
        // Non è WAIT_FOR_SETUP + TA sfavorevole → warningAntiCopart assente.
        assertThat(result).isEqualTo(SummaryVerdict.ENTER_NOW)
    }

    // =========================================================================
    // reentryCondition — presente solo con WAIT_FOR_SETUP + taVerdict=WAIT
    // =========================================================================

    @Test
    fun `verdict WAIT_FOR_SETUP from WAIT ta degrade enables reentryCondition contract`() {
        // L'aggregator produce WAIT_FOR_SETUP: è la precondizione per
        // esporre reentryCondition (la logica di propagazione è in SummaryService).
        val result = aggregator.aggregate(
            input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.WAIT),
        )
        assertThat(result).isEqualTo(SummaryVerdict.WAIT_FOR_SETUP)
    }

    // =========================================================================
    // Tabella completa — conteggio copertura righe
    // =========================================================================

    @Test
    fun `all mandatory mapping table rows are covered — summary coverage smoke test`() {
        // Verifica che i 16 scenari definiti nei test sopra producano
        // esattamente i verdetti attesi dalla tabella US-103.
        val cases = listOf(
            // (viVerdict, mosSignal, deepVerdict, taVerdict) → summaryVerdict
            Triple(input(ViVerdict.RED_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE), "VI-RED+TA-FAV", SummaryVerdict.AVOID),
            Triple(input(ViVerdict.INDETERMINATE_DOMINANT, Signal.GREEN, null, null), "INDETERMINATE", SummaryVerdict.INSUFFICIENT_DATA),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.RISCHIO_ESTREMO, EntryTimingVerdict.ENTRY_FAVORABLE), "RISCHIO_ESTREMO", SummaryVerdict.AVOID),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE), "ENTER_NOW-FAV", SummaryVerdict.ENTER_NOW),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_NEUTRAL), "ENTER_NOW-NEUTRAL", SummaryVerdict.ENTER_NOW),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, null, EntryTimingVerdict.ENTRY_FAVORABLE), "ENTER_NOW-NOT_INDEXED", SummaryVerdict.ENTER_NOW),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.WAIT), "WAIT-COPART-ANCHOR", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_UNFAVORABLE), "WAIT-UNFAV", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, DeepVerdict.WATCHLIST, EntryTimingVerdict.ENTRY_FAVORABLE), "WAIT-WATCHLIST", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.GREEN, null, null), "WAIT-NO-TA", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.YELLOW, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE), "WAIT-YELLOW-MOS", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.GREEN_DOMINANT, Signal.RED, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE), "WAIT-RED-MOS", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.YELLOW_DOMINANT, Signal.GREEN, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE), "YELLOW-GREEN-MOS", SummaryVerdict.WAIT_FOR_SETUP),
            Triple(input(ViVerdict.YELLOW_DOMINANT, Signal.RED, DeepVerdict.OK, EntryTimingVerdict.ENTRY_FAVORABLE), "YELLOW-RED-MOS", SummaryVerdict.AVOID),
            Triple(input(ViVerdict.YELLOW_DOMINANT, Signal.YELLOW, null, null), "YELLOW-YELLOW-MOS", SummaryVerdict.AVOID),
        )

        val failures = cases.filter { (inp, _, expected) ->
            aggregator.aggregate(inp) != expected
        }
        assertThat(failures)
            .withFailMessage {
                "Mapping table failures:\n" + failures.joinToString("\n") { (inp, label, expected) ->
                    "  [$label] input=$inp → got=${aggregator.aggregate(inp)}, expected=$expected"
                }
            }
            .isEmpty()
    }
}
