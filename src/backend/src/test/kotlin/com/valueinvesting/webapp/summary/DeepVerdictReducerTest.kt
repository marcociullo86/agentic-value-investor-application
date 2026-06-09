package com.valueinvesting.webapp.summary

import com.valueinvesting.webapp.service.LivelloRischio
import com.valueinvesting.webapp.service.VerdictClass
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

// Unit test per DeepVerdictReducer (TSK-341 / US-103).
//
// Verifica la riduzione da (VerdictClass × LivelloRischio) ai 3 segnali
// del gate Summary [DeepVerdict]: OK / WATCHLIST / RISCHIO_ESTREMO.
//
// Regola prioritaria: livelloRischio = RISCHIO_ESTREMO → DeepVerdict.RISCHIO_ESTREMO
// (override assoluto, regola assoluta US-103 §"Regola assoluta" + munger §Cascade Logica).
//
// Pure-function, nessun Spring context, nessuna I/O.
//
// [^src: management/kanban/EP-024-.../US-103-.../TSK-341.md]
// [^src: design_&_architecture/decisions/ADR-030-decision-layer-vi-ta-summary.md §3]
// [^src: wiki/concepts/munger-inversion-rag.md §Cascade Logica]
class DeepVerdictReducerTest {

    private lateinit var reducer: DeepVerdictReducer

    @BeforeEach
    fun setUp() {
        reducer = DeepVerdictReducer()
    }

    // =========================================================================
    // Override RISCHIO_ESTREMO (regola assoluta)
    // =========================================================================

    @Test
    fun `LivelloRischio RISCHIO_ESTREMO overrides APPROVATO to RISCHIO_ESTREMO`() {
        assertThat(reducer.reduce(VerdictClass.APPROVATO, LivelloRischio.RISCHIO_ESTREMO))
            .isEqualTo(DeepVerdict.RISCHIO_ESTREMO)
    }

    @Test
    fun `LivelloRischio RISCHIO_ESTREMO overrides APPROVATO_PANIC_BUY to RISCHIO_ESTREMO`() {
        assertThat(reducer.reduce(VerdictClass.APPROVATO_PANIC_BUY, LivelloRischio.RISCHIO_ESTREMO))
            .isEqualTo(DeepVerdict.RISCHIO_ESTREMO)
    }

    @Test
    fun `LivelloRischio RISCHIO_ESTREMO overrides WATCHLIST to RISCHIO_ESTREMO`() {
        assertThat(reducer.reduce(VerdictClass.WATCHLIST, LivelloRischio.RISCHIO_ESTREMO))
            .isEqualTo(DeepVerdict.RISCHIO_ESTREMO)
    }

    @Test
    fun `LivelloRischio RISCHIO_ESTREMO overrides all BOCCIATO variants to RISCHIO_ESTREMO`() {
        listOf(
            VerdictClass.BOCCIATO_NUMERICO,
            VerdictClass.BOCCIATO_QUALITATIVO,
            VerdictClass.BOCCIATO_VALUE_TRAP,
        ).forEach { verdetto ->
            assertThat(reducer.reduce(verdetto, LivelloRischio.RISCHIO_ESTREMO))
                .withFailMessage("Expected RISCHIO_ESTREMO for $verdetto + RISCHIO_ESTREMO")
                .isEqualTo(DeepVerdict.RISCHIO_ESTREMO)
        }
    }

    // =========================================================================
    // APPROVATO / APPROVATO_PANIC_BUY → OK (non RISCHIO_ESTREMO)
    // =========================================================================

    @Test
    fun `APPROVATO with RISCHIO_BASSO reduces to OK`() {
        assertThat(reducer.reduce(VerdictClass.APPROVATO, LivelloRischio.RISCHIO_BASSO))
            .isEqualTo(DeepVerdict.OK)
    }

    @Test
    fun `APPROVATO with RISCHIO_MODERATO reduces to OK`() {
        assertThat(reducer.reduce(VerdictClass.APPROVATO, LivelloRischio.RISCHIO_MODERATO))
            .isEqualTo(DeepVerdict.OK)
    }

    @Test
    fun `APPROVATO with RISCHIO_ALTO reduces to OK`() {
        assertThat(reducer.reduce(VerdictClass.APPROVATO, LivelloRischio.RISCHIO_ALTO))
            .isEqualTo(DeepVerdict.OK)
    }

    @Test
    fun `APPROVATO_PANIC_BUY with RISCHIO_BASSO reduces to OK`() {
        assertThat(reducer.reduce(VerdictClass.APPROVATO_PANIC_BUY, LivelloRischio.RISCHIO_BASSO))
            .isEqualTo(DeepVerdict.OK)
    }

    // =========================================================================
    // WATCHLIST → WATCHLIST
    // =========================================================================

    @Test
    fun `WATCHLIST with RISCHIO_MODERATO reduces to WATCHLIST`() {
        assertThat(reducer.reduce(VerdictClass.WATCHLIST, LivelloRischio.RISCHIO_MODERATO))
            .isEqualTo(DeepVerdict.WATCHLIST)
    }

    // =========================================================================
    // BOCCIATO_* → WATCHLIST (degrada il Summary a WAIT_FOR_SETUP/AVOID)
    // =========================================================================

    @Test
    fun `BOCCIATO_NUMERICO with RISCHIO_ALTO reduces to WATCHLIST`() {
        assertThat(reducer.reduce(VerdictClass.BOCCIATO_NUMERICO, LivelloRischio.RISCHIO_ALTO))
            .isEqualTo(DeepVerdict.WATCHLIST)
    }

    @Test
    fun `BOCCIATO_QUALITATIVO with RISCHIO_MODERATO reduces to WATCHLIST`() {
        assertThat(reducer.reduce(VerdictClass.BOCCIATO_QUALITATIVO, LivelloRischio.RISCHIO_MODERATO))
            .isEqualTo(DeepVerdict.WATCHLIST)
    }

    @Test
    fun `BOCCIATO_VALUE_TRAP with RISCHIO_BASSO reduces to WATCHLIST`() {
        assertThat(reducer.reduce(VerdictClass.BOCCIATO_VALUE_TRAP, LivelloRischio.RISCHIO_BASSO))
            .isEqualTo(DeepVerdict.WATCHLIST)
    }

    // =========================================================================
    // Tutte le combinazioni non-RISCHIO_ESTREMO: BOCCIATO_* mai produce OK
    // =========================================================================

    @Test
    fun `no BOCCIATO variant with non-RISCHIO_ESTREMO reduces to OK`() {
        val bocciatoVariants = listOf(
            VerdictClass.BOCCIATO_NUMERICO,
            VerdictClass.BOCCIATO_QUALITATIVO,
            VerdictClass.BOCCIATO_VALUE_TRAP,
        )
        val nonEstremoLivelli = listOf(
            LivelloRischio.RISCHIO_BASSO,
            LivelloRischio.RISCHIO_MODERATO,
            LivelloRischio.RISCHIO_ALTO,
        )
        val unexpectedOk = bocciatoVariants.flatMap { v ->
            nonEstremoLivelli.map { l -> v to l }
        }.filter { (v, l) -> reducer.reduce(v, l) == DeepVerdict.OK }

        assertThat(unexpectedOk)
            .withFailMessage { "BOCCIATO variants must never reduce to OK: found $unexpectedOk" }
            .isEmpty()
    }
}
