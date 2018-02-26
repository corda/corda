package net.corda.behave.process

import java.io.File
import java.time.Duration

class JarCommandWithMain(
        jarFile: File,
        mainClass: String,
        arguments: Array<String>,
        directory: File,
        timeout: Duration,
        enableRemoteDebugging: Boolean = false
) : Command(
        command = listOf(
                "/usr/bin/java",
                *extraArguments(enableRemoteDebugging),
                "-cp", "/Users/josecoll/IdeaProjects/corda-reviews/experimental/behave/build/libs/behave-3.0-SNAPSHOT.jar;$jarFile",
                mainClass,
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