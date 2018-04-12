@file:JvmName("ArtemisUtils")

package net.corda.nodeapi.internal

import java.nio.file.FileSystems
import java.nio.file.Path

/**
 * Require that the [Path] is on a default file system, and therefore is one that Artemis is willing to use.
 * @throws IllegalArgumentException if the path is not on a default file system.
 */
fun Path.requireOnDefaultFileSystem() {
    require(fileSystem == FileSystems.getDefault()) { "Artemis only uses the default file system" }
}

fun requireMessageSize(messageSize: Int, limit: Int) {
    require(messageSize <= limit) { "Message exceeds maxMessageSize network parameter, maxMessageSize: [$messageSize], message size: [$limit]" }
}
