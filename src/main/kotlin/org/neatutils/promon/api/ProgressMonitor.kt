package org.neatutils.promon.api

import org.neatutils.promon.internal.RootProgressMonitor

interface ProgressMonitor {

    companion object {

        @JvmStatic
        fun create(allowCancelation: Boolean = false): ProgressMonitor {
            return RootProgressMonitor(
                allowCancelation = allowCancelation
            )
        }

    }

    fun <T> task(name: String, totalWork: Int, run: () -> T): T

    fun task(name: String, totalWork: Int, run: () -> Unit) {
        this.task<Unit>(name, totalWork){ run() }
    }

    fun <T> subTask(workToAllocate: Int, run: (ProgressMonitor) -> T): T

    fun subTask(workToAllocate: Int, run: (ProgressMonitor) -> Unit) {
        this.subTask<Unit>(workToAllocate, run)
    }

    fun worked(work: Int)

    fun cancel()

    val isCanceled: Boolean

    fun addListener(listener: ProgressMonitorListener)

    fun removeListener(listener: ProgressMonitorListener): Boolean

    val listeners: List<ProgressMonitorListener>

    val progress: Progress
}