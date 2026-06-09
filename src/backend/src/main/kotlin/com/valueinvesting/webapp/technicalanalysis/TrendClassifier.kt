package com.valueinvesting.webapp.technicalanalysis

// Pure-function classifier per il trend primario daily.
// Vedi US-098 §"Classificazione del trend primario (deterministica)" — TSK-325.
//
// Slope SMA200: regressione lineare semplice sulle ultime 20 chiusure SMA200.
// Soglia di significativita' (SLOPE_SIGNIFICANCE_PCT_PER_DAY) e' espressa come
// frazione del valore SMA per giorno: |slope| < soglia ⇒ "piatto" ⇒ SIDEWAYS
// anche se prezzo > SMA50 > SMA200. Soglia tarata a 0.05% / sed = ~12.5%/anno,
// scelta documentata: sotto questa soglia il trend di fondo non e' considerato
// statisticamente direzionale dal punto di vista del Triple Screen Elder.
// [^src: wiki/syntheses/ta-entry-timing-stock-detail.md §"Screen 1 — Trend di lungo"]
object TrendClassifier {

    // Numero minimo di sedute per poter classificare il trend.
    // SMA200 non e' calcolabile sotto 200 chiusure storiche.
    const val MIN_HISTORY_DAYS: Int = 200

    // Finestra usata per la regressione lineare dello slope SMA200.
    const val SLOPE_WINDOW: Int = 20

    // Soglia di "piattezza" dello slope in unita' SMA / seduta.
    // Espressa come frazione del valore medio SMA nella finestra (es. 0.0005 =
    // 0.05%/sed). Sotto questa soglia il trend e' considerato non direzionale.
    const val SLOPE_SIGNIFICANCE_PCT_PER_DAY: Double = 0.0005

    /**
     * Classifica il trend primario daily.
     *
     * @param price          prezzo corrente del titolo (USD).
     * @param sma50          SMA50 ultimo punto (USD).
     * @param sma200         SMA200 ultimo punto (USD).
     * @param sma200Series   serie SMA200 ordinata cronologicamente (asc).
     *                       Solo le ultime [SLOPE_WINDOW] entrate sono usate.
     * @param historyDays    numero di sedute EOD disponibili per il ticker.
     */
    fun classify(
        price: Double?,
        sma50: Double?,
        sma200: Double?,
        sma200Series: List<Double>,
        historyDays: Int,
    ): TrendClassification {
        // INDETERMINATE: storico insufficiente o input mancanti.
        if (historyDays < MIN_HISTORY_DAYS) return TrendClassification.INDETERMINATE
        if (price == null || price <= 0.0) return TrendClassification.INDETERMINATE
        if (sma50 == null || sma50 <= 0.0) return TrendClassification.INDETERMINATE
        if (sma200 == null || sma200 <= 0.0) return TrendClassification.INDETERMINATE

        // Slope SMA200 via regressione lineare sulla finestra recente.
        val slope = linearRegressionSlope(sma200Series.takeLast(SLOPE_WINDOW))
        if (slope == null) return TrendClassification.INDETERMINATE

        // Normalizza lo slope in frazione del valore SMA200 corrente per renderlo
        // confrontabile con la soglia (independente dal livello di prezzo del
        // titolo: titolo a $5 e titolo a $500 condividono la stessa soglia in %).
        val slopePctPerDay = slope / sma200
        val isUpStack = price > sma50 && sma50 > sma200
        val isDownStack = price < sma50 && sma50 < sma200

        return when {
            isUpStack && slopePctPerDay > SLOPE_SIGNIFICANCE_PCT_PER_DAY -> TrendClassification.UPTREND
            isDownStack && slopePctPerDay < -SLOPE_SIGNIFICANCE_PCT_PER_DAY -> TrendClassification.DOWNTREND
            else -> TrendClassification.SIDEWAYS
        }
    }

    /**
     * Regressione lineare semplice y = mx + q sulle coppie (i, y_i). Ritorna
     * il coefficiente m. Null se la finestra ha < 2 punti o varianza zero.
     */
    internal fun linearRegressionSlope(window: List<Double>): Double? {
        val n = window.size
        if (n < 2) return null
        val xMean = (n - 1) / 2.0
        val yMean = window.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val xDev = i - xMean
            num += xDev * (window[i] - yMean)
            den += xDev * xDev
        }
        if (den == 0.0) return null
        return num / den
    }
}
