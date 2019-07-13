package org.neatutils.promon.api

interface ProgressMonitorListener {

    fun onWorked(executedWorkload: Double) {
        // by default, this is a no-op. Override in subclasses.
    }

    fun onTaskStarted(name: String, totalWork: Int){
        // by default, this is a no-op. Override in subclasses.
    }

    fun onTaskFinished(name: String){
        // by default, this is a no-op. Override in subclasses.
    }

    fun onTaskFailed(name: String, cause: Throwable){
        // by default, this is a no-op. Override in subclasses.
    }

    fun onCancelRequested(){
        // by default, this is a no-op. Override in subclasses.
    }

    fun onTaskCanceled(progress: Progress){

    }

}