/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

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
    require(messageSize <= limit) { "Message exceeds maxMessageSize network parameter, maxMessageSize: [$limit], message size: [$messageSize]" }
}
