package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.*

private val HEADLESS_FLAG = "--headless"
private val SCREEN_FLAG = "--screen"
private val CAPSULE_DEBUG_FLAG = "--capsule-debug"

private val os by lazy {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
    if ("mac" in osName || "darwin" in osName) OS.MACOS
    else if ("win" in osName) OS.WINDOWS
    else OS.LINUX
}

private enum class OS { MACOS, WINDOWS, LINUX }

private object debugPortAlloc {
    private var basePort = 5005
    internal fun next() = basePort++
}

private object monitoringPortAlloc {
    private var basePort = 7005
    internal fun next() = basePort++

}

fun main(args: Array<String>) {
    val startedProcesses = mutableListOf<Process>()

    val headless = GraphicsEnvironment.isHeadless() || args.contains(HEADLESS_FLAG)
    val capsuleDebugMode = args.contains(CAPSULE_DEBUG_FLAG)
    val workingDir = File(System.getProperty("user.dir"))
    val jvmArgs = if (capsuleDebugMode) listOf("-Dcapsule.log=verbose") else emptyList()
    println("isHeadLess: $headless")
    println("Starting nodes in $workingDir")
    workingDir.listFiles { file -> file.isDirectory }.forEach { dir ->
        listOf(NodeJarType, WebJarType).forEach { jarType ->
            jarType.acceptDirAndStartProcess(dir, headless, args.toList(), jvmArgs)?.let { startedProcesses += it }
        }
    }
    println("Started ${startedProcesses.size} processes")
    println("Finished starting nodes")



}

private abstract class JarType(private val jarName: String) {
    internal abstract fun acceptNodeConf(nodeConf: File): Boolean

    internal fun acceptDirAndStartProcess(dir: File, isHeadless: Boolean, javaArgs: List<String>, jvmArgs: List<String>): Process? {
        if (!File(dir, jarName).exists()) {
            return null
        }
        if (!File(dir, "node.conf").let { it.exists() && acceptNodeConf(it) }) {
            return null
        }
        val debugPort = debugPortAlloc.next()
        val monitoringPort = monitoringPortAlloc.next()
        println("Starting $jarName in $dir on debug port $debugPort")
        return getLauncher(isHeadless, jarName, dir, debugPort, monitoringPort, javaArgs, jvmArgs).toProcess(dir).process
    }

    private fun getLauncher(headless: Boolean, jarName: String, dir: File, debugPort: Int, monitoringPort: Int, javaArgs: List<String>, jvmArgs: List<String>): Launcher {
        if (headless) {
            return HeadlessLauncher(jarName, dir, debugPort, monitoringPort, javaArgs, jvmArgs)
        } else {
            return getLauncherBasedOnOsAndEnvironment(jarName, dir, debugPort, monitoringPort, javaArgs, jvmArgs)
        }

    }
}

private object NodeJarType : JarType("corda.jar") {
    override fun acceptNodeConf(nodeConf: File) = true
}

private object WebJarType : JarType("corda-webserver.jar") {
    // TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
    override fun acceptNodeConf(nodeConf: File) = Files.lines(nodeConf.toPath()).anyMatch { "webAddress" in it }
}

private abstract class Launcher(
        jarName: String,
        internal val dir: File,
        debugPort: Int?,
        monitoringPort: Int?,
        internal val nodeName: String,
        init: MutableList<String>.() -> Unit,
        val args: List<String>,
        jvmArgs: List<String>
) {
    private val jolokiaJar by lazy {
        File("$dir/drivers").listFiles { _, filename ->
            filename.matches("jolokia-jvm-.*-agent\\.jar$".toRegex())
        }.first().name
    }

    internal val javaCommand: List<String> = mutableListOf<String>().apply {
        add(getJavaPath())
        addAll(jvmArgs)
        add("-Dname=$\"$nodeName\"")
        val jvmArgs: MutableList<String> = mutableListOf()
        null != debugPort && jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")
        null != monitoringPort && jvmArgs.add("-javaagent:drivers/$jolokiaJar=port=$monitoringPort")
        if (jvmArgs.isNotEmpty()) {
            add("-Dcapsule.jvm.args=${jvmArgs.joinToString(separator = " ")}")
        }
        add("-jar")
        add(jarName)
        init()
        addAll(args.filter { it != HEADLESS_FLAG && it != CAPSULE_DEBUG_FLAG && it != SCREEN_FLAG })
    }

    //    internal abstract fun toProcessBuilder(): ProcessBuilder
    internal abstract fun toProcess(dir: File): RunningProcess

    fun unixCommand() = javaCommand.map(::quotedFormOf).joinToString(" ")

    fun getJavaPath() = File(File(System.getProperty("java.home"), "bin"), "java").path
}

private fun getLauncherBasedOnOsAndEnvironment(jarName: String, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>): Launcher {
    when (os) {
        OS.LINUX -> {
            return LinuxLauncher(jarName, dir, debugPort, monitoringPort, args, jvmArgs)
        }
        OS.MACOS -> {
            return OsXLauncher(jarName, dir, debugPort, monitoringPort, args, jvmArgs)
        }
        OS.WINDOWS -> {
            return WindowsLauncher(jarName, dir, debugPort, monitoringPort, args, jvmArgs)
        }
    }

}

private class HeadlessLauncher(jarName: String, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>)
    : Launcher(jarName, dir, debugPort, monitoringPort, dir.name, { add("--no-local-shell") }, args, jvmArgs) {
    private fun toProcessBuilder() = ProcessBuilder(javaCommand).redirectError(File("error.$nodeName.log")).inheritIO()
    override fun toProcess(dir: File): RunningProcess {
        val processBuilder = toProcessBuilder()
        return RunningProcess(processBuilder.start(), processBuilder.command().joinToString(" "))
    }

}

private class OsXLauncher(jarName: String, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>)
    : Launcher(jarName, dir, debugPort, monitoringPort, "${dir.name}-$jarName", {}, args, jvmArgs) {
    override fun toProcess(dir: File): RunningProcess {
        val processCommand = listOf("osascript", "-e", """
            tell app "Terminal"
            activate
            delay 0.5
            tell app "System Events" to tell process "Terminal" to keystroke "t" using javaCommand down
            delay 0.5
            do script "bash -c 'cd \"$dir\" ; \"${javaCommand.joinToString("""\" \"""")}\" && exit'" in selected tab of the front window
            end tell
        """)
        print("Sleeping for 500 millis to allow OSX terminal to catch up")
        Thread.sleep(500)
        return RunningProcess(ProcessBuilder(processCommand).directory(dir).start(), processCommand.joinToString(" "));
    }
}


private class LinuxLauncher(jarName: String, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>)
    : Launcher(jarName, dir, debugPort, monitoringPort, "${dir.name}-$jarName", {}, args, jvmArgs) {
    override fun toProcess(dir: File): RunningProcess {
        val commandLineAndProcess: Pair<Process, String> =
                if (isTmux()) {
                    val processBuilder = ProcessBuilder(listOf("tmux", "new-window", "-n", "\"$nodeName\"", "${unixCommand()}; [ $? -eq 0 -o $? -eq 143 ] || sh"))
                            .directory(dir)
                    processBuilder.start() to processBuilder.command().joinToString(" ")
                } else if (shouldUseScreen(args)) {
                    val tempFile = File.createTempFile("cordaScreen", ".sh")
                    tempFile.deleteOnExit()
                    val commandString = """
                        cd "${dir.absolutePath}"
                        screen -dmS "${nodeName}" sh -c '${javaCommand.joinToString(" ")}'
                        echo started ${nodeName}
                    """
                    tempFile.writeBytes(commandString.toByteArray())
                    val processBuilder = ProcessBuilder("sh", tempFile.absolutePath.toString()).directory(dir)
                    processBuilder.start() to processBuilder.command().joinToString(" ")
                } else {
                    val processBuilder = ProcessBuilder(listOf("xterm", "-T", "\"$nodeName\"", "-e", "${unixCommand()}; [ $? -eq 0 -o $? -eq 143 ] || sh")).directory(dir)
                    processBuilder.start() to processBuilder.command().joinToString(" ")
                }
        return RunningProcess(commandLineAndProcess.first, commandLineAndProcess.second)
    }
}

private class WindowsLauncher(jarName: String, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>)
    : Launcher(jarName, dir, debugPort, monitoringPort, "${dir.name}-$jarName", {}, args, jvmArgs) {
    override fun toProcess(dir: File): RunningProcess {
        val commandLine = listOf("cmd", "/C", "start ${javaCommand.joinToString(" ") { windowsSpaceEscape(it) }}")
        return RunningProcess(ProcessBuilder(commandLine).directory(dir).start(), commandLine.joinToString(" "))
    }

    private fun windowsSpaceEscape(s: String) = s.replace(" ", "\" \"")
}

private fun quotedFormOf(text: String) = "'${text.replace("'", "'\\''")}'" // Suitable for UNIX shells.
private fun isTmux() = System.getenv("TMUX")?.isNotEmpty() ?: false

private fun shouldUseScreen(args: List<String>): Boolean {
    val process = ProcessBuilder("which", "screen").start()
    process.waitFor()
    return (process.inputStream.reader(Charsets.UTF_8).readLines().size) == 1 && args.contains(SCREEN_FLAG)
}


private data class RunningProcess(val process: Process, val commandLine: String)
