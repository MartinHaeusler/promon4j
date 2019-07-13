package org.neatutils.promon.internal

import kotlin.concurrent.withLock

class SubProgressMonitor(
    private val workAllocatedInParent: Int,
    private val parent: ProgressMonitorInternal
) : AbstractProgressMonitor() {

    override fun cancel() {
        this.parent.cancel()
    }

    override val isCanceled: Boolean
        get() = this.parent.isCanceled


    // =================================================================================================================
    // INTERNAL HELPER FUNCTIONS
    // =================================================================================================================

    override fun workedSilently(work: Int) {
        require(work >= 0) { "Precondition violation - argument 'work' must not be negative!" }
        this.lock.withLock {
            super.workedSilently(work)
            // report to parent
            val percentage = this.workedSoFar.toDouble() / this.totalWork.toDouble()
            this.parent.workedByChild(this, this.workAllocatedInParent * percentage)
        }
    }

}