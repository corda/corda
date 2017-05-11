package net.corda.node.services.transactions

import net.corda.core.atexit
import net.corda.core.exists
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

internal class DeleteOnExitPath(internal val path: Path) {
    private val shutdownHook = atexit { dispose() }
    internal fun dispose() {
        path.toFile().deleteRecursively()
        shutdownHook.cancel()
    }
}

open class PathHandle internal constructor(private val deleteOnExitPath: DeleteOnExitPath, private val handleCounter: AtomicInteger) : Closeable {
    val path
        get() = deleteOnExitPath.path.also {
            0 == handleCounter.get() && throw IllegalStateException("Defunct path: $it")
        }

    init {
        handleCounter.incrementAndGet()
    }

    fun handle() = PathHandle(deleteOnExitPath, handleCounter)

    override fun close() {
        if (0 == handleCounter.decrementAndGet()) {
            deleteOnExitPath.dispose()
        }
    }
}

open class PathManager(path: Path) : PathHandle(DeleteOnExitPath(path), AtomicInteger())
