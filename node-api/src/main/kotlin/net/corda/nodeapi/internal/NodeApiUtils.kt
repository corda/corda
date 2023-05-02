@file:Suppress("LongParameterList", "MagicNumber")

package net.corda.nodeapi.internal

import io.netty.util.concurrent.DefaultThreadFactory
import net.corda.core.utilities.seconds
import java.time.Duration
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Creates a [ThreadPoolExecutor] which will use a maximum of [maxPoolSize] threads at any given time and will by default idle down to 0
 * threads.
 */
fun namedThreadPoolExecutor(maxPoolSize: Int,
                            corePoolSize: Int = 0,
                            idleKeepAlive: Duration = 30.seconds,
                            workQueue: BlockingQueue<Runnable> = LinkedBlockingQueue(),
                            poolName: String = "pool",
                            daemonThreads: Boolean = false,
                            threadPriority: Int = Thread.NORM_PRIORITY): ThreadPoolExecutor {
    return ThreadPoolExecutor(
            corePoolSize,
            maxPoolSize,
            idleKeepAlive.toNanos(),
            TimeUnit.NANOSECONDS,
            workQueue,
            DefaultThreadFactory(poolName, daemonThreads, threadPriority)
    )
}
