package net.corda.testing.driver

import net.corda.core.div
import java.nio.file.Path

object ProcessUtilities {
    inline fun <reified C : Any> startJavaProcess(
            arguments: List<String>,
            classpath: String = defaultClassPath,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList(),
            inheritIO: Boolean = true,
            errorLogPath: Path? = null,
            workingDirectory: Path? = null
    ): Process {
        return startJavaProcess(C::class.java.name, arguments, classpath, jdwpPort, extraJvmArguments, inheritIO, errorLogPath, workingDirectory)
    }

    fun startJavaProcess(
            className: String,
            arguments: List<String>,
            classpath: String = defaultClassPath,
            jdwpPort: Int? = null,
            extraJvmArguments: List<String> = emptyList(),
            inheritIO: Boolean = true,
            errorLogPath: Path? = null,
            workingDirectory: Path? = null
    ): Process {
        val allArguments = mutableListOf<String>().apply {
            add((System.getProperty("java.home") / "bin" / "java").toString())
            if (jdwpPort != null) add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
            add("-Xmx200m")
            add("-XX:+UseG1GC")
            addAll(extraJvmArguments)
            add("-Dlog4j.configurationFile=classpath:log4j2-corda.xml")
            add("-cp")
            add(classpath)
            add(className)
            addAll(arguments)
        }
        return ProcessBuilder(allArguments).apply {
            if (errorLogPath != null) redirectError(errorLogPath.toFile())
            if (inheritIO) inheritIO()
            if (workingDirectory != null) directory(workingDirectory.toFile())
        }.start()
    }

    val defaultClassPath: String get() = System.getProperty("java.class.path")
}
