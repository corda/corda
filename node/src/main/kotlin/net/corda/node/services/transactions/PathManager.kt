package net.corda.node.services.transactions

import net.corda.core.internal.addShutdownHook
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

internal class DeleteOnExitPath(internal val path: Path) {
    private val shutdownHook = addShutdownHook { dispose() }
    internal fun dispose() {
        path.toFile().deleteRecursively()
        shutdownHook.cancel()
    }
}

open class PathHandle internal constructor(private val deleteOnExitPath: DeleteOnExitPath, private val handleCounter: AtomicInteger) : Closeable {
    val path
        get(): Path {
            val path = deleteOnExitPath.path
            check(handleCounter.get() != 0) { "Defunct path: $path" }
            return path
        }

    init {
        handleCounter.incrementAndGet()
    }

    fun handle() = PathHandle(deleteOnExitPath, handleCounter)

    override fun close() {
        if (handleCounter.decrementAndGet() == 0) {
            deleteOnExitPath.dispose()
        }
    }
}

/**
 * An instance of this class is a handle on a temporary [path].
 * If necessary, additional handles on the same path can be created using the [handle] method.
 * The path is (recursively) deleted when [close] is called on the last handle, typically at the end of a [use] expression.
 * The value of eager cleanup of temporary files is that there are cases when shutdown hooks don't run e.g. SIGKILL.
 */
open class PathManager(path: Path) : PathHandle(DeleteOnExitPath(path), AtomicInteger())
