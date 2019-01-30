@file:DeleteForDJVM

package net.corda.core.internal

import co.paralleluniverse.strands.concurrent.ReentrantLock
import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import net.corda.core.DeleteForDJVM
import kotlin.concurrent.withLock

private val pooledScanMutex = ReentrantLock()

/**
 * Use this rather than the built in implementation of [scan] on [ClassGraph].  The built in implementation of [scan] creates
 * a thread pool every time resulting in too many threads.  This one uses a mutex to restrict concurrency.
 */
fun ClassGraph.pooledScan(): ScanResult {
    return pooledScanMutex.withLock { this@pooledScan.scan() }
}
