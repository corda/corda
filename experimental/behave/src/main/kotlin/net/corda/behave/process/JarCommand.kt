package net.corda.behave.process

import java.io.File
import java.time.Duration

class JarCommand(
        val jarFile: File,
        arguments: Array<String>,
        directory: File,
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