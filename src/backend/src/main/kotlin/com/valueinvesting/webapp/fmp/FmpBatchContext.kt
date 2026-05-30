package com.valueinvesting.webapp.fmp

// Flag thread-local "siamo dentro il batch notturno TopValuePicksJob?".
//
// PERCHE'
// -------
// Il rate limiter FMP `fmp` (280/min) e' UNICO e condiviso online+batch (il
// limite FMP e' per-API-key, ADR-016 §Appendice A). Ma online e batch vogliono
// comportamenti DIVERSI quando il bucket si esaurisce nella finestra corrente:
//   - online (UI): fail-fast (timeout 2s -> RequestNotPermitted -> degrada),
//     l'utente non puo' attendere il refresh;
//   - batch: NON perdere la chiamata — bloccarsi, attendere il refresh del
//     bucket (~1 min) e ritentare la stessa chiamata, poi proseguire.
// La scelta dipende dal CONTESTO runtime della chiamata, non dal servizio
// (DeepAnalysisService e' dual-use): per questo serve un flag per-thread.
//
// COME
// ----
// Il batch e' single-thread e SINCRONO: screen() (screener + 13-F + NewsScout)
// e il loop DeepAnalysisService girano tutti sul thread che esegue
// TopValuePicksJob.run(). Settando il flag all'inizio di run() (via runInBatch,
// con ripristino in finally) tutte le chiamate FMP discendenti lo vedono, e
// ResilientFmpAdapter lo legge per scegliere il comportamento sul rate limiter.
//
// VINCOLO
// -------
// Il ThreadLocal NON attraversa i confini di thread (executor / @Async /
// parallelStream). Il batch e' volutamente sincrono: NON introdurre fan-out
// parallelo dentro run() senza propagare il contesto (es. Spring TaskDecorator),
// altrimenti i worker tornerebbero al comportamento online (fail-fast).
object FmpBatchContext {

    private val batchMode = ThreadLocal.withInitial { false }

    fun isBatch(): Boolean = batchMode.get()

    /**
     * Attiva/disattiva il flag batch sul thread corrente. Il chiamante DEVE
     * disattivarlo in un `finally` (vedi TopValuePicksJob.run()) per non lasciare
     * il flag attivo su un thread di un pool che verrebbe riusato.
     */
    fun setBatch(value: Boolean) {
        if (value) batchMode.set(true) else batchMode.remove()
    }
}
