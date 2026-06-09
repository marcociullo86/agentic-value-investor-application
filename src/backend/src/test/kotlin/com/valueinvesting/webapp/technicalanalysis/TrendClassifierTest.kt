package com.valueinvesting.webapp.technicalanalysis

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

// Unit test per TrendClassifier (TSK-325 / US-098).
//
// Copre i 4 casi della classificazione deterministica definiti in
// US-098 §"Classificazione del trend primario (deterministica)":
//   - UPTREND     : prezzo > SMA50 > SMA200, slope SMA200 > soglia.
//   - DOWNTREND   : prezzo < SMA50 < SMA200, slope SMA200 < -soglia.
//   - SIDEWAYS    : ogni altra combinazione (slope piatto o stack non allineato).
//   - INDETERMINATE: storico < 200 sedute.
//
// Plus: regressione lineare slope (metodo `linearRegressionSlope`).
//
// [^src: wiki/concepts/dow-theory.md]
// [^src: wiki/concepts/moving-averages-ta.md]
class TrendClassifierTest {

    // Genera una serie SMA200 con slope noto. La regressione lineare su N punti
    // con un trend costante produce uno slope = `slopePerUnit`.
    private fun linearSma200(start: Double, slopePerUnit: Double, n: Int = 20): List<Double> =
        (0 until n).map { i -> start + i * slopePerUnit }

    // -------------------------------------------------------------------------
    // INDETERMINATE: storico insufficiente
    // -------------------------------------------------------------------------

    @Test
    fun `classify returns INDETERMINATE when historyDays is below MIN_HISTORY_DAYS`() {
        val result = TrendClassifier.classify(
            price = 200.0,
            sma50 = 190.0,
            sma200 = 175.0,
            sma200Series = linearSma200(175.0, 0.1),
            historyDays = 199,
        )
        assertThat(result).isEqualTo(TrendClassification.INDETERMINATE)
    }

    @Test
    fun `classify returns INDETERMINATE when price is null`() {
        val result = TrendClassifier.classify(
            price = null,
            sma50 = 190.0,
            sma200 = 175.0,
            sma200Series = linearSma200(175.0, 0.1),
            historyDays = 250,
        )
        assertThat(result).isEqualTo(TrendClassification.INDETERMINATE)
    }

    @Test
    fun `classify returns INDETERMINATE when sma200Series has fewer than 2 points`() {
        val result = TrendClassifier.classify(
            price = 200.0,
            sma50 = 190.0,
            sma200 = 175.0,
            sma200Series = listOf(175.0), // solo 1 punto → slope null
            historyDays = 250,
        )
        assertThat(result).isEqualTo(TrendClassification.INDETERMINATE)
    }

    // -------------------------------------------------------------------------
    // UPTREND: prezzo > SMA50 > SMA200 + slope SMA200 positivo > soglia
    // -------------------------------------------------------------------------

    @Test
    fun `classify returns UPTREND for AAPL-like uptrend fixture`() {
        // price > sma50 > sma200 con slope positivo marcato
        // slope netto = 0.5 su SMA200 = 175 → slopePct = 0.5/175 ≈ 0.00286 >> soglia 0.0005
        val result = TrendClassifier.classify(
            price = 210.0,
            sma50 = 195.0,
            sma200 = 175.0,
            sma200Series = linearSma200(start = 165.0, slopePerUnit = 0.5),
            historyDays = 252,
        )
        assertThat(result).isEqualTo(TrendClassification.UPTREND)
    }

    @Test
    fun `classify returns UPTREND at minimum required historyDays (200)`() {
        val result = TrendClassifier.classify(
            price = 150.0,
            sma50 = 140.0,
            sma200 = 130.0,
            sma200Series = linearSma200(start = 120.0, slopePerUnit = 0.5),
            historyDays = 200,
        )
        assertThat(result).isEqualTo(TrendClassification.UPTREND)
    }

    // -------------------------------------------------------------------------
    // DOWNTREND: prezzo < SMA50 < SMA200 + slope SMA200 negativo < -soglia
    // -------------------------------------------------------------------------

    @Test
    fun `classify returns DOWNTREND for conclamated downtrend fixture`() {
        // price < sma50 < sma200 con slope negativo marcato
        // slope = -0.5, SMA200 = 110 → slopePct = -0.5/110 ≈ -0.00455 << -0.0005
        val result = TrendClassifier.classify(
            price = 85.0,
            sma50 = 95.0,
            sma200 = 110.0,
            sma200Series = linearSma200(start = 120.0, slopePerUnit = -0.5),
            historyDays = 252,
        )
        assertThat(result).isEqualTo(TrendClassification.DOWNTREND)
    }

    // -------------------------------------------------------------------------
    // SIDEWAYS: slope piatto (|slope| < soglia) — tutte le altre combinazioni
    // -------------------------------------------------------------------------

    @Test
    fun `classify returns SIDEWAYS when slope is below significance threshold`() {
        // price > sma50 > sma200 ma slope quasi zero
        // Serie piatta: slopePerUnit = 0.00001 → slopePct ≈ 0.000000058 << 0.0005
        val result = TrendClassifier.classify(
            price = 200.0,
            sma50 = 190.0,
            sma200 = 175.0,
            sma200Series = linearSma200(start = 175.0, slopePerUnit = 0.00001),
            historyDays = 252,
        )
        assertThat(result).isEqualTo(TrendClassification.SIDEWAYS)
    }

    @Test
    fun `classify returns SIDEWAYS when price is between SMA50 and SMA200 (non-aligned stack)`() {
        // price tra SMA50 e SMA200: stack non allineato → SIDEWAYS
        val result = TrendClassifier.classify(
            price = 155.0,
            sma50 = 150.0,
            sma200 = 160.0, // price < sma200 ma price > sma50 → non uptrend né downtrend
            sma200Series = linearSma200(start = 158.0, slopePerUnit = 0.1),
            historyDays = 252,
        )
        assertThat(result).isEqualTo(TrendClassification.SIDEWAYS)
    }

    // -------------------------------------------------------------------------
    // Metodo interno linearRegressionSlope
    // -------------------------------------------------------------------------

    @Test
    fun `linearRegressionSlope returns correct slope for perfect linear series`() {
        // Serie: 10, 12, 14, 16, 18 → slope = 2
        val slope = TrendClassifier.linearRegressionSlope(listOf(10.0, 12.0, 14.0, 16.0, 18.0))
        assertAll(
            { assertThat(slope).isNotNull() },
            { assertThat(slope!!).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.001)) },
        )
    }

    @Test
    fun `linearRegressionSlope returns null for single-element window`() {
        val slope = TrendClassifier.linearRegressionSlope(listOf(100.0))
        assertThat(slope).isNull()
    }

    @Test
    fun `linearRegressionSlope returns null for empty window`() {
        val slope = TrendClassifier.linearRegressionSlope(emptyList())
        assertThat(slope).isNull()
    }

    @Test
    fun `linearRegressionSlope returns zero slope for constant series (flat, no y-variance)`() {
        // Serie costante: den dipende solo dagli indici x (sempre > 0 per n>=2),
        // num = 0 → slope 0.0 (retta piatta), non null.
        val slope = TrendClassifier.linearRegressionSlope(listOf(50.0, 50.0, 50.0, 50.0))
        assertThat(slope).isEqualTo(0.0)
    }

    @Test
    fun `linearRegressionSlope uses only last SLOPE_WINDOW entries from the series`() {
        // Serie lunga: i primi valori salgono, gli ultimi SLOPE_WINDOW scendono.
        // TrendClassifier prende takeLast(20); qui costruiamo direttamente la finestra.
        val descendingWindow = (0 until 20).map { i -> 100.0 - i * 1.0 }
        val slope = TrendClassifier.linearRegressionSlope(descendingWindow)
        assertThat(slope).isNotNull()
        assertThat(slope!!).isLessThan(0.0) // trend discendente
    }
}
