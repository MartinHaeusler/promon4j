package org.neatutils.promon.api

interface Progress {

    val taskName: String
    val workPercentage: Double
    val totalWork: Int
    val workDone: Int
    val taskStatus: TaskStatus

    val completedSubtaskProgresses: List<Progress>
    val currentSubtasksProgresses: List<Progress>

}