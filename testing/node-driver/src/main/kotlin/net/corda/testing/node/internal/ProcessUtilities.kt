package net.corda.testing.node.internal

import net.corda.core.internal.div
import java.io.File
import java.nio.file.Path

object ProcessUtilities {
    @Suppress("LongParameterList")
    inline fun <reified C : Any> startJavaProcess(
            arguments: List<String>,
            classPath: List<String> = defaultClassPath,
            workingDirectory: Path? = null,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList(),
            maximumHeapSize: String? = null,
            environmentVariables: Map<String, String> = emptyMap()
    ): Process {
        return startJavaProcess(
                C::class.java.name,
                arguments,
                classPath,
                workingDirectory,
                jdwpPort,
                extraJvmArguments,
                maximumHeapSize,
                environmentVariables = environmentVariables
        )
    }

    @Suppress("LongParameterList")
    fun startJavaProcess(
            className: String,
            arguments: List<String>,
            classPath: List<String> = defaultClassPath,
            workingDirectory: Path? = null,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList(),
            maximumHeapSize: String? = null,
            identifier: String = "",
            environmentVariables: Map<String,String> = emptyMap(),
            inheritIO: Boolean = true
    ): Process {
        val command = mutableListOf<String>().apply {
            add(javaPath)
            (jdwpPort != null) && add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
            if (maximumHeapSize != null) add("-Xmx$maximumHeapSize")
            add("-XX:+UseG1GC")
            addAll(extraJvmArguments)
            add(className)
            addAll(arguments)
        }
        return ProcessBuilder(command).apply {
            if (inheritIO) inheritIO()
            environment().putAll(environmentVariables)
            environment()["CLASSPATH"] = classPath.joinToString(File.pathSeparator)
            if (workingDirectory != null) {
                // An identifier may be handy if the same process started, killed and then re-started. Without the identifier
                // StdOut and StdErr will be overwritten. By default the identifier is a timestamp passed down to here.
                redirectError((workingDirectory / "$className.$identifier.stderr.log").toFile())
                redirectOutput((workingDirectory / "$className.$identifier.stdout.log").toFile())
                directory(workingDirectory.toFile())
            }
        }.start()
    }

    private val javaPath = (System.getProperty("java.home") / "bin" / "java").toString()

    val defaultClassPath: List<String> = System.getProperty("java.class.path").split(File.pathSeparator)
}