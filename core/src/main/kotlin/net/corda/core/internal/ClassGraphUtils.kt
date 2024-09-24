package net.corda.core.internal

import io.github.classgraph.ClassGraph
import io.github.classgraph.ScanResult
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

private val pooledScanMutex = ReentrantLock()

/**
 * Use this rather than the built-in implementation of [ClassGraph.scan]. The built-in implementation creates
 * a thread pool every time, resulting in too many threads. This one uses a mutex to restrict concurrency.
 */
fun ClassGraph.pooledScan(): ScanResult {
    return pooledScanMutex.withLock(::scan)
}
