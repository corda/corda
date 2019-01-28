@file:DeleteForDJVM

package net.corda.core.internal

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import net.corda.core.DeleteForDJVM
import java.util.concurrent.Executors

// ClassGraph seems to default to a minimum of 2, so we follow suit.
private val poolSize = Math.max(2, Runtime.getRuntime().availableProcessors())
private val pooledScanExecutorService = Executors.newFixedThreadPool(poolSize)

/**
 * Use this rather than the built in implementation of [scan] on [ClassGraph].  The built in implementation of [scan] creates
 * a thread pool every time resulting in too many threads.  This one uses a shared executor with restricted concurrency.
 */
fun ClassGraph.pooledScan(): ScanResult {
    return this.scan(net.corda.core.internal.pooledScanExecutorService, net.corda.core.internal.poolSize)
}
