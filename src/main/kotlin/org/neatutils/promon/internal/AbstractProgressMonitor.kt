package org.neatutils.promon.internal

import org.neatutils.promon.api.Progress
import org.neatutils.promon.api.ProgressMonitor
import org.neatutils.promon.api.ProgressMonitorListener
import org.neatutils.promon.api.TaskStatus
import org.neatutils.promon.api.exceptions.ProgressMonitorCanceledException
import java.util.*
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

abstract class AbstractProgressMonitor : ProgressMonitorInternal {

    protected val lock: Lock = ReentrantLock(true)
    protected val listenerSet: MutableSet<ProgressMonitorListener> = mutableSetOf()

    protected val completedSubProgressMonitors: MutableList<SubProgressMonitor> = mutableListOf()
    protected val workReportedByCurrentSubTaskMonitors: MutableMap<SubProgressMonitor, Double> = mutableMapOf()

    protected var mainTaskName: String = ""
    protected var totalWork: Int = -1
    protected var workedSoFar: Int = 0
    protected var taskStatus: TaskStatus = TaskStatus.NOT_STARTED_YET

    override fun <T> task(name: String, totalWork: Int, run: () -> T): T {
        require(totalWork > 0) { "Precondition violation - argument 'totalWork' must be positive!" }
        require(name.isNotBlank()) { "Precondition violation - argument 'name' must not be blank!" }
        var successful = false
        try {
            this.lock.withLock {
                check(this.mainTaskName == "") { "Precondition violation - task(...)  has already been called on this monitor!" }
                this.mainTaskName = name
                this.totalWork = totalWork
                this.fireEvent { it.onTaskStarted(name, totalWork) }
                // before we even start the work, make sure we're not canceled already
                if (this.isCanceled) {
                    throw ProgressMonitorCanceledException("Progress Monitor for task [${name}] has been canceled!")
                }
                this.taskStatus = TaskStatus.RUNNING
            }
            val result = run()
            successful = true
            return result
        } catch (e: Throwable) {
            if (e is ProgressMonitorCanceledException) {
                this.taskStatus = TaskStatus.CANCELED
                this.fireEvent { it.onTaskCanceled(this.progress!!) }
            } else {
                this.taskStatus = TaskStatus.FAILED
                this.fireEvent { it.onTaskFailed(name, e) }
            }
            throw e
        } finally {
            if (successful) {
                this.taskStatus = TaskStatus.COMPLETED
                this.workedSoFar = this.totalWork
                this.fireEvent { it.onTaskFinished(name) }
            } else if (this.taskStatus != TaskStatus.CANCELED) {
                this.taskStatus = TaskStatus.FAILED
            }
        }
    }

    override fun <T> subTask(workToAllocate: Int, run: (ProgressMonitor) -> T): T {
        check(this.totalWork > 0) { "Precondition violation - cannot call 'subTask' before a root task has been started!" }
        require(workToAllocate > 0) { "Precondition violation - argument 'workToAllocate' must be greater than zero!" }
        require(workToAllocate < this.totalWork) { "Precondition violation - argument 'workToAllocate' for sub task must not exceed total work of parent task!" }
        var successful = false
        val subProgressMonitor = SubProgressMonitor(workToAllocate, this)
        try {
            this.lock.withLock {
                this.workReportedByCurrentSubTaskMonitors.put(subProgressMonitor, 0.0)
                // before we even start the work, make sure we're not canceled already
                if (this.isCanceled) {
                    throw ProgressMonitorCanceledException("Progress Monitor for task [${this.taskName}] has been canceled!")
                }
                this.taskStatus = TaskStatus.RUNNING
            }
            val result = run(subProgressMonitor)
            successful = true
            return result
        } finally {
            this.lock.withLock {
                if (successful) {
                    // apply the work we received from the subtask (avoid "overshooting" of work from subtask)
                    this.workedSilently(workToAllocate)
                    this.completedSubProgressMonitors.add(subProgressMonitor)
                } else {
                    this.taskStatus = TaskStatus.FAILED
                }
                this.workReportedByCurrentSubTaskMonitors.remove(subProgressMonitor)
            }
        }
    }

    override fun worked(work: Int) {
        this.lock.withLock {
            this.workedSilently(work)
            this.fireEvent { it.onWorked(work.toDouble()) }
        }
    }

    override fun workedByChild(childProgressMonitor: SubProgressMonitor, totalParentWorkUnits: Double) {
        this.lock.withLock {
            val currentWorked = this.workReportedByCurrentSubTaskMonitors[childProgressMonitor] ?: 0.0
            this.workReportedByCurrentSubTaskMonitors[childProgressMonitor] = totalParentWorkUnits
            this.fireEvent { it.onWorked(totalParentWorkUnits - currentWorked) }
        }
    }


    override fun addListener(listener: ProgressMonitorListener) {
        this.lock.withLock {
            this.listenerSet.add(listener)
        }
    }

    override fun removeListener(listener: ProgressMonitorListener): Boolean {
        this.lock.withLock {
            return this.listenerSet.remove(listener)
        }
    }

    override val listeners: List<ProgressMonitorListener>
        get() {
            this.lock.withLock {
                return Collections.unmodifiableList(this.listenerSet.toList())
            }
        }

    override val progress: Progress
        get() = this.lock.withLock {
            val workDone =
                min(this.totalWork, this.workedSoFar + this.workReportedByCurrentSubTaskMonitors.values.sum().toInt())
            val workPercentage = when (totalWork) {
                0 -> 0.0
                else -> workDone / totalWork.toDouble()
            }
            ProgressImpl(
                taskName = this.mainTaskName,
                completedSubtaskProgresses = this.completedSubProgressMonitors.map { it.progress },
                currentSubtasksProgresses = this.workReportedByCurrentSubTaskMonitors.keys.map { it.progress },
                workPercentage = workPercentage,
                totalWork = this.totalWork,
                workDone = workDone,
                taskStatus = this.taskStatus
            )
        }

    override val taskName: String?
        get() = this.mainTaskName

    override fun toString(): String {
        return "ProMon[${this.taskName}]"
    }

    // =================================================================================================================
    // INTERNAL HELPER FUNCTIONS
    // =================================================================================================================

    open protected fun workedSilently(work: Int) {
        require(work >= 0) { "Precondition violation - argument 'work' must not be negative!" }
        this.lock.withLock {
            if (this.isCanceled) {
                throw ProgressMonitorCanceledException("Task execution has been canceled!")
            }
            if (this.workedSoFar >= this.totalWork) {
                // ignore excess reported work
                return
            }
            if (this.workedSoFar + work >= this.totalWork) {
                this.workedSoFar = this.totalWork
            } else {
                this.workedSoFar += work
            }
        }
    }

    protected val workPercentage: Double
        get() {
            this.lock.withLock {
                if (this.totalWork <= 0) {
                    // not started yet!
                    return 0.0
                }
                return this.workedSoFar.toDouble() / this.totalWork.toDouble()
            }
        }

    protected fun fireEvent(event: (ProgressMonitorListener) -> Unit) {
        this.lock.withLock {
            this.listenerSet.forEach(event)
        }
    }
}