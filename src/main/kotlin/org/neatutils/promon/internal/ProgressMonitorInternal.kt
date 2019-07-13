package org.neatutils.promon.internal

import org.neatutils.promon.api.ProgressMonitor

interface ProgressMonitorInternal : ProgressMonitor {

    fun workedByChild(childProgressMonitor: SubProgressMonitor, totalParentWorkUnits: Double)

    val taskName: String?

}