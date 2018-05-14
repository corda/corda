/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.behave.process

import java.nio.file.Path
import java.time.Duration

class JarCommand(
        val jarFile: Path,
        arguments: Array<out String>,
        directory: Path,
        timeout: Duration,
        enableRemoteDebugging: Boolean = false
) : Command(
        command = listOf(
                "/usr/bin/java",
                *extraArguments(enableRemoteDebugging),
                "-jar", "$jarFile",
                *arguments
        ),
        directory = directory,
        timeout = timeout
) {

    companion object {

        private fun extraArguments(enableRemoteDebugging: Boolean) =
                if (enableRemoteDebugging) {
                    arrayOf("-Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
                } else {
                    arrayOf()
                }
    }
}