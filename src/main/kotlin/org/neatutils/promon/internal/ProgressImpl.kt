package org.neatutils.promon.internal

import org.neatutils.promon.api.Progress
import org.neatutils.promon.api.TaskStatus

class ProgressImpl(
    override val taskName: String,
    override val workPercentage: Double,
    override val totalWork: Int,
    override val workDone: Int,
    override val taskStatus: TaskStatus,
    override val completedSubtaskProgresses: List<Progress>,
    override val currentSubtasksProgresses: List<Progress>
) : Progress