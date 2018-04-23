/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.node.services.transactions

import net.corda.core.internal.uncheckedCast
import net.corda.nodeapi.internal.addShutdownHook
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

private class DeleteOnExitPath(internal val path: Path) {
    private val shutdownHook = addShutdownHook { dispose() }
    internal fun dispose() {
        path.toFile().deleteRecursively()
        shutdownHook.cancel()
    }
}

/**
 * An instance of this class is a handle on a temporary [path].
 * If necessary, additional handles on the same path can be created using the [handle] method.
 * The path is (recursively) deleted when [close] is called on the last handle, typically at the end of a [use] expression.
 * The value of eager cleanup of temporary files is that there are cases when shutdown hooks don't run e.g. SIGKILL.
 */
open class PathManager<T : PathManager<T>>(path: Path) : Closeable {
    private val deleteOnExitPath = DeleteOnExitPath(path)
    private val handleCounter = AtomicInteger(1)
    val path
        get(): Path {
            val path = deleteOnExitPath.path
            check(handleCounter.get() != 0) { "Defunct path: $path" }
            return path
        }

    fun handle(): T {
        handleCounter.incrementAndGet()
        return uncheckedCast(this)
    }

    override fun close() {
        if (handleCounter.decrementAndGet() == 0) {
            deleteOnExitPath.dispose()
        }
    }
}
