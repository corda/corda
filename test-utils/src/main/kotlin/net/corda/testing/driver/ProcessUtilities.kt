package net.corda.testing.driver

import net.corda.core.internal.div
import net.corda.core.internal.exists
import java.io.File.pathSeparator
import java.nio.file.Path

object ProcessUtilities {
    inline fun <reified C : Any> startJavaProcess(
            arguments: List<String>,
            jdwpPort: Int? = null
    ): Process {
        return startJavaProcessImpl(C::class.java.name, arguments, defaultClassPath, jdwpPort, emptyList(), null, null)
    }

    fun startCordaProcess(
            className: String,
            arguments: List<String>,
            jdwpPort: Int?,
            extraJvmArguments: List<String>,
            errorLogPath: Path?,
            workingDirectory: Path? = null
    ): Process {
        // FIXME: Instead of hacking our classpath, use the correct classpath for className.
        val classpath = defaultClassPath.split(pathSeparator).filter { !(it / "log4j2-test.xml").exists() }.joinToString(pathSeparator)
        return startJavaProcessImpl(className, arguments, classpath, jdwpPort, extraJvmArguments, errorLogPath, workingDirectory)
    }

    fun startJavaProcessImpl(
            className: String,
            arguments: List<String>,
            classpath: String,
            jdwpPort: Int?,
            extraJvmArguments: List<String>,
            errorLogPath: Path?,
            workingDirectory: Path?
    ): Process {
        val command = mutableListOf<String>().apply {
            add((System.getProperty("java.home") / "bin" / "java").toString())
            (jdwpPort != null) && add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
            add("-Xmx256m")
            add("-XX:+UseG1GC")
            addAll(extraJvmArguments)
            add("-cp")
            add(classpath)
            add(className)
            addAll(arguments)
        }
        return ProcessBuilder(command).apply {
            if (errorLogPath != null) redirectError(errorLogPath.toFile()) // FIXME: Undone by inheritIO.
            inheritIO()
            if (workingDirectory != null) directory(workingDirectory.toFile())
        }.start()
    }

    val defaultClassPath: String get() = System.getProperty("java.class.path")
}
