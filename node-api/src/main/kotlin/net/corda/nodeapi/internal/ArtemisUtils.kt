@file:JvmName("ArtemisUtils")
package net.corda.nodeapi.internal

import net.corda.core.internal.declaredField
import org.apache.activemq.artemis.utils.actors.ProcessorBase
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.atomic.AtomicInteger

/**
 * Require that the [Path] is on a default file system, and therefore is one that Artemis is willing to use.
 * @throws IllegalArgumentException if the path is not on a default file system.
 */
fun Path.requireOnDefaultFileSystem() {
    require(fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
}

fun requireMessageSize(messageSize: Int, limit: Int) {
    require(messageSize <= limit) { "Message exceeds maxMessageSize network parameter, maxMessageSize: [$limit], message size: [$messageSize]" }
}

val Executor.rootExecutor: Executor get() {
    var executor: Executor = this
    while (executor is ProcessorBase<*>) {
        executor = executor.declaredField<Executor>("delegate").value
    }
    return executor
}

fun Executor.setThreadPoolName(threadPoolName: String) {
    (rootExecutor as? ThreadPoolExecutor)?.let { it.threadFactory = NamedThreadFactory(threadPoolName, it.threadFactory) }
}

private class NamedThreadFactory(poolName: String, private val delegate: ThreadFactory) : ThreadFactory {
    companion object {
        private val poolId = AtomicInteger(0)
    }

    private val prefix = "$poolName-${poolId.incrementAndGet()}-"
    private val nextId = AtomicInteger(0)

    override fun newThread(r: Runnable): Thread {
        val thread = delegate.newThread(r)
        thread.name = "$prefix${nextId.incrementAndGet()}"
        return thread
    }
}
