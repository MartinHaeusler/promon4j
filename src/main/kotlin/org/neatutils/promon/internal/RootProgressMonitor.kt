package org.neatutils.promon.internal

import org.neatutils.promon.api.exceptions.MonitorIsNotCancelableException
import java.util.concurrent.atomic.AtomicBoolean

class RootProgressMonitor(
    val allowCancelation: Boolean
) : AbstractProgressMonitor() {

    private val cancelRequested: AtomicBoolean = AtomicBoolean(false)

    // =================================================================================================================
    // PUBLIC API
    // =================================================================================================================

    override fun cancel() {
        if (!this.allowCancelation) {
            throw MonitorIsNotCancelableException("This progress monitor is not cancelable!")
        }
        if (this.isCanceled) {
            // multiple cancellations have no effect
            return
        }
        this.cancelRequested.set(true)
        this.fireEvent { it.onCancelRequested() }
    }

    override val isCanceled: Boolean
        get() = this.cancelRequested.get()


}