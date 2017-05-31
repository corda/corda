package net.corda.core.utilities

import java.nio.file.Path

// TODO This doesn't belong in core and can be moved into node
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
        val separator = System.getProperty("file.separator")
        val javaPath = System.getProperty("java.home") + separator + "bin" + separator + "java"
        val debugPortArgument = if (jdwpPort != null) {
            listOf("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$jdwpPort")
        } else {
            emptyList()
        }

        val allArguments = listOf(javaPath) +
                debugPortArgument +
                listOf("-Xmx200m", "-XX:+UseG1GC") +
                extraJvmArguments +
                listOf("-cp", classpath, className) +
                arguments.toList()
        return ProcessBuilder(allArguments).apply {
            if (errorLogPath != null) redirectError(errorLogPath.toFile())
            if (inheritIO) inheritIO()
            if (workingDirectory != null) directory(workingDirectory.toFile())
        }.start()
    }

    val defaultClassPath: String get() = System.getProperty("java.class.path")
}
