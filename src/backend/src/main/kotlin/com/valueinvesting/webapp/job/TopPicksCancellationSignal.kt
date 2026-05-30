package com.valueinvesting.webapp.job

import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

// Cooperative cancellation signal for the Top Value Picks batch run.
//
// WHY a separate bean — `TopPicksManualTrigger` depends on `TopValuePicksJob`
// (it runs it). If the job also depended on the trigger to read a cancel flag
// we'd introduce a circular dependency. This tiny shared holder breaks the
// cycle: both the trigger (writer, via the cancel endpoint) and the job
// (reader, polling inside its loop) depend on it instead of on each other.
//
// CONTRACT — cooperative, not preemptive. Setting the flag does NOT interrupt
// the running thread; `TopValuePicksJob.run()` polls `isCancelRequested()` at
// the top of each ticker iteration and bails out cleanly (run log → ABORTED,
// no partial upsert). The flag is cleared at the START of every run (manual via
// the trigger, scheduled via the cron tick) so a stale cancel from a previous
// run can never abort the next one.
@Component
class TopPicksCancellationSignal {

    private val cancelRequested = AtomicBoolean(false)

    fun request() {
        cancelRequested.set(true)
    }

    fun clear() {
        cancelRequested.set(false)
    }

    fun isCancelRequested(): Boolean = cancelRequested.get()
}
