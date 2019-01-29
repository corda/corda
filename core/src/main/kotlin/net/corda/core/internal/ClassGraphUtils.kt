@file:DeleteForDJVM

package net.corda.core.internal

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import net.corda.core.DeleteForDJVM
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

// ClassGraph seems to default to a minimum of 2, so we follow suit.
private val poolSize = Math.max(2, Runtime.getRuntime().availableProcessors())
private val pooledScanExecutorService = ThreadPoolExecutor(poolSize, poolSize, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue<Runnable>(), object : ThreadFactory {

    private val counter = AtomicInteger(0)
    override fun newThread(runnable: Runnable?): Thread {
        val thread = Thread(runnable, "ClassGraph-pooled-${counter.getAndIncrement()}")
        thread.isDaemon = true
        return thread
    }
})

/**
 * Use this rather than the built in implementation of [scan] on [ClassGraph].  The built in implementation of [scan] creates
 * a thread pool every time resulting in too many threads.  This one uses a shared executor with restricted concurrency.
 */
fun ClassGraph.pooledScan(): ScanResult {
    return this.scan(net.corda.core.internal.pooledScanExecutorService, net.corda.core.internal.poolSize)
}
