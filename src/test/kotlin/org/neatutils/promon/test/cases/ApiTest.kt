package org.neatutils.promon.test.cases

import org.junit.Test
import org.neatutils.promon.api.Progress
import org.neatutils.promon.api.ProgressMonitor
import org.neatutils.promon.api.ProgressMonitorListener
import org.neatutils.promon.api.TaskStatus
import org.neatutils.promon.api.exceptions.MonitorIsNotCancelableException
import org.neatutils.promon.api.exceptions.ProgressMonitorCanceledException
import kotlin.random.Random
import kotlin.test.*

class ApiTest {

    @Test
    fun basicExample() {
        val monitor = ProgressMonitor.create()
        monitor.task("Hello World", 10) {
            repeat(10) {
                monitor.worked(1)
            }
        }

        val progress = monitor.progress
        assertNotNull(progress)
        assertEquals(TaskStatus.COMPLETED, progress.taskStatus)
        assertEquals("Hello World", progress.taskName)
        assertEquals(1.0, progress.workPercentage)
        assertEquals(10, progress.workDone)
        assertEquals(10, progress.totalWork)
    }

    @Test
    fun basicExampleWithListener() {
        val monitor = ProgressMonitor.create()

        var reportedWork = 0.0
        var receivedFinish = false

        val listener = object : ProgressMonitorListener {

            override fun onTaskFinished(name: String) {
                receivedFinish = true
            }

            override fun onWorked(executedWorkload: Double) {
                reportedWork += executedWorkload
            }
        }

        monitor.addListener(listener)

        monitor.task("Hello World", 10) {
            repeat(10) {
                monitor.worked(1)
            }
        }

        assertEquals(10.0, reportedWork)
        assertTrue(receivedFinish)
    }

    @Test
    fun basicExampleWithConcurrency() {
        val monitor = ProgressMonitor.create()

        monitor.task("Hello World", 100) {
            inParallel(10) {
                repeat(10) {
                    monitor.worked(1)
                    Thread.sleep(Random.nextLong(3))
                }
            }
            assertEquals(100, monitor.progress.workDone)
            // even though progress is at 100%, the task
            // will not count as "completed" while we're
            // inside the task run lambda
            assertEquals(TaskStatus.RUNNING, monitor.progress.taskStatus)
        }

        assertEquals(100, monitor.progress.workDone)
        assertEquals(TaskStatus.COMPLETED, monitor.progress.taskStatus)
    }

    @Test
    fun subProgressMonitorExample() {
        val monitor = ProgressMonitor.create()
        monitor.task("Parent Task", 100) {
            // we allocate 10 of our 100 work points for sub task 1.
            monitor.subTask(10) { subMonitor ->
                // our sub task 1 itself has 1000 work points, which
                // will account for 10 in the root monitor.
                subMonitor.task("Sub 1", 1000) {
                    subMonitor.worked(500)
                    // if we report work to the sub monitor,
                    // it is automatically reported to the parent.
                    // 500 of 1000 points for the sub-task mean 50%
                    // of the sub task work is done. The parent had
                    // 10 work points allocated, so our 50% progress
                    // account for 5 points of work in the parent.
                    assertEquals(5, monitor.progress.workDone)
                    assertEquals(TaskStatus.RUNNING, subMonitor.progress.taskStatus)
                    assertEquals(TaskStatus.RUNNING, monitor.progress.taskStatus)
                    subMonitor.worked(500)
                }
                assertEquals(TaskStatus.COMPLETED, subMonitor.progress.taskStatus)
                assertEquals(TaskStatus.RUNNING, monitor.progress.taskStatus)
            }
            // we allocate 90 of our 100 work points for sub task 2.
            monitor.subTask(90) { subMonitor ->
                // our sub task 2 itself has 10 work points, which
                // will account for 90 in the root monitor.
                subMonitor.task("Sub 2", 10) {
                    assertEquals(10, monitor.progress.workDone)
                    subMonitor.worked(2)
                    assertEquals((10 + 0.2 * 90).toInt(), monitor.progress.workDone)
                    subMonitor.worked(2)
                    assertEquals((10 + 0.4 * 90).toInt(), monitor.progress.workDone)
                    subMonitor.worked(2)
                    assertEquals((10 + 0.6 * 90).toInt(), monitor.progress.workDone)
                    subMonitor.worked(2)
                    assertEquals((10 + 0.8 * 90).toInt(), monitor.progress.workDone)
                    subMonitor.worked(2)
                    assertEquals(100, monitor.progress.workDone)
                }
            }
        }
    }

    @Test(expected = MonitorIsNotCancelableException::class)
    fun progressMonitorsAreNotCancelableByDefault() {
        val monitor = ProgressMonitor.create()
        monitor.cancel()
    }

    @Test
    fun canCancelMonitor() {
        val monitor = ProgressMonitor.create(allowCancelation = true)

        try {
            monitor.task("Cancel me", 100) {
                for (i in 0..100) {
                    monitor.worked(1)
                    if (i == 42) {
                        monitor.cancel()
                    }
                }
            }
            fail("Failed to cancel progress monitor!")
        } catch (expected: ProgressMonitorCanceledException) {
            // pass
        }

        assertEquals(TaskStatus.CANCELED, monitor.progress.taskStatus)
    }

    @Test
    fun canCancelMonitorBeforeTaskRuns() {
        val monitor = ProgressMonitor.create(allowCancelation = true)
        monitor.cancel()
        try {
            monitor.task("Will never run", 1) {
                fail("Managed to run a task on a canceled monitor!")
            }
            fail("Failed to cancel progress monitor!")
        } catch (expected: ProgressMonitorCanceledException) {
            // pass
        }

        assertEquals(TaskStatus.CANCELED, monitor.progress.taskStatus)
    }

    @Test
    fun cancellingSubMonitorCancelsTheParent() {
        val monitor = ProgressMonitor.create(allowCancelation = true)
        try {
            monitor.task("Root", 10) {
                monitor.subTask(5) { subMonitor ->
                    subMonitor.worked(5)
                    subMonitor.cancel()
                }
                monitor.subTask(5) {
                    fail("Cancellation did not affect second subtask!")
                }
            }

        } catch (expected: ProgressMonitorCanceledException) {
            // pass
        }

        assertEquals(TaskStatus.CANCELED, monitor.progress.taskStatus)
    }

    @Test
    fun onlyTheFirstCancellationRequestHasAnEffect() {
        val monitor = ProgressMonitor.create(allowCancelation = true)
        var reportedCancellationRequests = 0
        var reportedTaskCancels = 0

        monitor.addListener(object : ProgressMonitorListener {

            override fun onCancelRequested() {
                reportedCancellationRequests++
            }

            override fun onTaskCanceled(progress: Progress) {
                reportedTaskCancels++
            }
        })

        try {
            monitor.cancel()
            monitor.cancel()
            monitor.cancel()
            monitor.task("This will never happen", 10) { }
            fail("Failed to cancel progress monitor!")
        } catch (expected: ProgressMonitorCanceledException) {
            // pass
        }

        assertEquals(TaskStatus.CANCELED, monitor.progress.taskStatus)
        assertEquals(1, reportedCancellationRequests)
        assertEquals(1, reportedTaskCancels)
    }

    @Test
    fun subTasksCannotOvershootTheirWorkload() {
        val monitor = ProgressMonitor.create()

        monitor.task("Root", 10) {
            monitor.subTask(4) { subMonitor ->
                subMonitor.task("Overshooting", 10) {
                    subMonitor.worked(3)
                    subMonitor.worked(3)
                    subMonitor.worked(3)
                    // this one overshoots the 10 work units
                    // of the sub monitor by 2.
                    subMonitor.worked(3)
                }
            }
            // yet, we only see that our 4 work units allocated
            // to the sub task have been assigned.
            assertEquals(4, monitor.progress.workDone)
            monitor.worked(6)
        }

        assertEquals(TaskStatus.COMPLETED, monitor.progress.taskStatus)
    }

    @Test
    fun subTasksWhichUnderdeliverTheirWorkloadAreSetToCompleted() {
        val monitor = ProgressMonitor.create()

        monitor.task("Root", 10) {
            monitor.subTask(4) { subMonitor ->
                subMonitor.task("Overshooting", 10) {
                    // this subtask under-delivers the 10 work units
                    // of the sub monitor by 1.
                    subMonitor.worked(3)
                    subMonitor.worked(3)
                    subMonitor.worked(3)
                }
                // since the subtask has not reached 100% yet,
                // the parent monitor does not have the full 4
                // work points associated with the subtask.
                assertTrue(monitor.progress.workDone < 4)
            }
            // yet, we only see that our 4 work units allocated
            // to the sub task have been assigned because the task
            // lambda completed.
            assertEquals(4, monitor.progress.workDone)
            monitor.worked(6)
        }

        assertEquals(TaskStatus.COMPLETED, monitor.progress.taskStatus)
    }

    @Test(expected = IllegalStateException::class)
    fun cannotStartMultipleRootTasks() {
        val monitor = ProgressMonitor.create()

        monitor.task("Root", 1) {}
        monitor.task("This is gonna fail", 1) {}
    }

    @Test(expected = IllegalStateException::class)
    fun cannotStartSubtaskWithoutRootTask(){
        val monitor = ProgressMonitor.create()

        monitor.subTask(100){ }
    }

    @Test
    fun canRunParallelSubtasks() {
        val monitor = ProgressMonitor.create()

        monitor.task("Parallel Subtasks", 1000) {
            inParallel(threads = 10) {
                monitor.subTask(100) { subMon ->
                    subMon.task(Thread.currentThread().name, 10) {
                        repeat(10) {
                            subMon.worked(1)
                            Thread.sleep(Random.nextLong(3))
                        }
                    }
                }
            }
        }

        val progress = monitor.progress
        assertNotNull(progress)
        assertEquals(TaskStatus.COMPLETED, progress.taskStatus)
        assertEquals(progress.totalWork, progress.workDone)
        assertEquals(10, progress.completedSubtaskProgresses.size)
    }

    @Test
    fun canNestSubTasks() {
        val monitor = ProgressMonitor.create()

        val rootProgressPercentages = mutableListOf<Double>()

        monitor.task("Nested Subtasks", 100) {
            rootProgressPercentages += monitor.progress.workPercentage
            monitor.subTask(40) { sub1 ->
                sub1.task("Sub 1", 100) {
                    sub1.subTask(80) { sub1a ->
                        sub1a.task("Sub 1a", 40) {
                            // under-deliver
                            rootProgressPercentages += monitor.progress.workPercentage
                            sub1a.worked(10)
                            rootProgressPercentages += monitor.progress.workPercentage
                            sub1a.worked(10)
                        }
                        rootProgressPercentages += monitor.progress.workPercentage
                    }
                    sub1.subTask(20) { sub1b ->
                        rootProgressPercentages += monitor.progress.workPercentage
                        sub1b.task("Sub 1b", 10) {
                            // over-deliver
                            rootProgressPercentages += monitor.progress.workPercentage
                            sub1b.worked(100)
                            rootProgressPercentages += monitor.progress.workPercentage
                        }
                    }
                }
            }
            rootProgressPercentages += monitor.progress.workPercentage
            // over-deliver
            monitor.subTask(80) { sub2 ->
                rootProgressPercentages += monitor.progress.workPercentage
                sub2.task("Sub 2", 10) {
                    sub2.subTask(4) { sub2a ->
                        rootProgressPercentages += monitor.progress.workPercentage
                        sub2a.task("Sub 2a", 100) {
                            sub2a.worked(100)
                            rootProgressPercentages += monitor.progress.workPercentage
                        }
                    }
                    rootProgressPercentages += monitor.progress.workPercentage
                    sub2.subTask(6) { sub2b ->
                        sub2b.task("Sub 2b", 80) {
                            sub2b.worked(50)
                            rootProgressPercentages += monitor.progress.workPercentage
                            sub2b.worked(30)
                        }
                    }
                    rootProgressPercentages += monitor.progress.workPercentage
                }
                rootProgressPercentages += monitor.progress.workPercentage
            }
        }

        val progress = monitor.progress
        assertNotNull(progress)
        assertEquals(TaskStatus.COMPLETED, progress.taskStatus)
        assertEquals(progress.totalWork, progress.workDone)
        progress.completedSubtaskProgresses.forEach { assertEquals(TaskStatus.COMPLETED, it.taskStatus) }

        for (i in 0 until rootProgressPercentages.size - 1) {
            assertTrue(rootProgressPercentages[i] <= rootProgressPercentages[i + 1])
        }
    }


    // =================================================================================================================
    // UTILITY METHODS
    // =================================================================================================================

    private fun inParallel(threads: Int, task: () -> Unit) {
        (0 until threads).asSequence().map {
            Thread(task)
        }.map { it.start(); it }.forEach { it.join() }
    }

}