package net.corda.testing.node.internal

import net.corda.core.internal.div
import java.nio.file.Path

object ProcessUtilities {
    inline fun <reified C : Any> startJavaProcess(
            arguments: List<String>,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList()
    ): Process {
        return startJavaProcessImpl(C::class.java.name, arguments, defaultClassPath, jdwpPort, extraJvmArguments, null, null, null)
    }

    fun startCordaProcess(
            className: String,
            arguments: List<String>,
            jdwpPort: Int?,
            extraJvmArguments: List<String>,
            errorLogPath: Path?,
            workingDirectory: Path?,
            maximumHeapSize: String
    ): Process {
        return startJavaProcessImpl(className, arguments, defaultClassPath, jdwpPort, extraJvmArguments, errorLogPath, workingDirectory, maximumHeapSize)
    }

    fun startJavaProcessImpl(
            className: String,
            arguments: List<String>,
            classpath: String,
            jdwpPort: Int?,
            extraJvmArguments: List<String>,
            errorLogPath: Path?,
            workingDirectory: Path?,
            maximumHeapSize: String?
    ): Process {
        val command = mutableListOf<String>().apply {
            add((System.getProperty("java.home") / "bin" / "java").toString())
            (jdwpPort != null) && add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
            if (maximumHeapSize != null) add("-Xmx$maximumHeapSize")
            add("-XX:+UseG1GC")
            addAll(extraJvmArguments)
            add(className)
            addAll(arguments)
        }
        return ProcessBuilder(command).apply {
            inheritIO()
            environment().put("CLASSPATH", classpath)
            if (workingDirectory != null) {
                redirectError((workingDirectory / "$className.stderr.log").toFile())
                redirectOutput((workingDirectory / "$className.stdout.log").toFile())
                directory(workingDirectory.toFile())
            }
        }.start()
    }

    val defaultClassPath: String get() = System.getProperty("java.class.path")
}