/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.utilities

import com.google.common.util.concurrent.SettableFuture
import io.netty.util.concurrent.FastThreadLocalThread
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.function.Supplier

/**
 * An extended executor interface that supports thread affinity assertions and short circuiting. This can be useful
 * for ensuring code runs on the right thread, and also for unit testing.
 */
interface AffinityExecutor : Executor {
    /** Returns true if the current thread is equal to the thread this executor is backed by. */
    val isOnThread: Boolean

    /** Throws an IllegalStateException if the current thread is not one of the threads this executor is backed by. */
    fun checkOnThread() {
        if (!isOnThread)
            throw IllegalStateException("On wrong thread: " + Thread.currentThread())
    }

    /** If isOnThread() then runnable is invoked immediately, otherwise the closure is queued onto the backing thread. */
    fun executeASAP(runnable: () -> Unit) {
        if (isOnThread)
            runnable()
        else
            execute(runnable)
    }

    // TODO: Rename this to executeWithResult
    /**
     * Runs the given function on the executor, blocking until the result is available. Be careful not to deadlock this
     * way! Make sure the executor can't possibly be waiting for the calling thread.
     */
    fun <T> fetchFrom(fetcher: () -> T): T {
        return if (isOnThread)
            fetcher()
        else
            CompletableFuture.supplyAsync(Supplier { fetcher() }, this).get()
    }

    /**
     * Run the executor until there are no tasks pending and none executing.
     */
    fun flush()

    /**
     * An executor backed by thread pool (which may often have a single thread) which makes it easy to schedule
     * tasks in the future and verify code is running on the executor.
     */
    open class ServiceAffinityExecutor(threadName: String, numThreads: Int) : AffinityExecutor,
            ScheduledThreadPoolExecutor(numThreads) {
        private val threads = Collections.synchronizedSet(HashSet<Thread>())

        init {
            setThreadFactory { runnable ->
                val thread = object : FastThreadLocalThread() {
                    override fun run() {
                        try {
                            runnable.run()
                        } finally {
                            threads -= this
                        }
                    }
                }
                thread.isDaemon = true
                thread.name = threadName
                threads += thread
                thread
            }
        }

        override val isOnThread: Boolean get() = Thread.currentThread() in threads

        override fun flush() {
            do {
                val f = SettableFuture.create<Boolean>()
                execute { f.set(queue.isEmpty() && activeCount == 1) }
            } while (!f.get())
        }
    }
}
