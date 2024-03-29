# promon4j - A Progress Monitor library for the JVM

promon4j is a Progress Monitor library written in Kotlin. It can be used from any language compliant with the JVM ecosystem. It is inspired by the way Progress Monitors are handled within the Eclipse IDE, but without the need for `try-finally` statements. It also takes care of most monitor-related thread synchronization issues and exception handling for you. promon4j is a zero-dependency library (except for the JRE and Kotlin runtime libraries) and should run in all Java 8+ environments.

# Installation

Download the repository, `cd` into the project root directory and run `./gradlew build` to produce a `*.jar` file (sorry, no maven version yet).

# Quickstart

The basic idea of promon4j is that you create a `ProgressMonitor`, then call its `task` method, declare how many "work points" your task has, and then call `worked(x)` to report progress:

```kotlin
// creates a new progress monitor
val monitor = ProgressMonitor.create()

// you need to know in advance how many
// work steps your task is going to have.
val totalWork = 10

// start the task
monitor.task("Hello World", totalWork) {
    // do your actual work here...
    repeat(10) {
        // ... and report progress
        monitor.worked(1)
    }
}

// access report data
monitor.progress.taskStatus // <-- TaskStatus.COMPLETED

```

I recommend to check out the tests in the repository for further usage examples.

> Attention: both `ProgressMonitor#task(...)` as well as `ProgressMonitor#subTask(...)` methods are **synchronous** calls. The action you pass in as a lambda will be called immediately, and exactly once.

# Features

 - Support for **task cancellation**
   - Must be enabled upon monitor creation via `ProgressMonitor.create(allowCancelation = true)`
   - A call to `ProgressMonitor#cancel()` will issue a cancelation request
   - After a cancelation request has come in, any attempt to call `worked(...)` or start a task will result in a `ProgressMonitorCanceledException`, allowing to exit out of the process.
 - **Listener** support to react to events (`ProgressMonitor#addListener(...)`) 
 - Support for **nested sub-tasks** with their own monitors
   - Allocate work points in the parent for the sub-task
   - Sub-task is free to define its own total workload (may be less than, equal to or even higher amount allocated in the parent)
 - Progress **reporting capabilities** (`ProgressMonitor#progress`) both during and after task execution
   - Reporting and execution also work when used concurrently (i.e. get a full progress report while your task is still running).
 - Support for a **single task** being executed **concurrently** by multiple threads
 - Support for **multiple sub-tasks** being executed **concurrently** by multiple threads
 
# What promon4j is not

This library provides progress monitoring. It does not provide a task system, nor does it assume any particular task system to be in place. Also, no GUI components are provided. The data provided by `ProgressMonitor#progress` can be used as input to any kind of renderer, including command line, desktop rendering (e.g. Swing, JavaFX) or even served via REST for client-side rendering.
