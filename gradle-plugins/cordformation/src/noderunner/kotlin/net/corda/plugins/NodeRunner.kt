package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.*

private val HEADLESS_FLAG = "--headless"
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
    val javaArgs = args.filter { it != HEADLESS_FLAG && it != CAPSULE_DEBUG_FLAG }
    val jvmArgs = if (capsuleDebugMode) listOf("-Dcapsule.log=verbose") else emptyList<String>()
    println("Starting nodes in $workingDir")
    workingDir.listFiles { file -> file.isDirectory }.forEach { dir ->
        listOf(NodeJarType, WebJarType).forEach { jarType ->
            jarType.acceptDirAndStartProcess(dir, headless, javaArgs, jvmArgs)?.let { startedProcesses += it }
        }
    }
    println("Started ${startedProcesses.size} processes")
    println("Finished starting nodes")
}

private abstract class JarType(internal val jarName: String, internal val jolokiaLogHandler: String? = null) {
    internal abstract fun acceptNodeConf(nodeConf: File): Boolean
    internal fun acceptDirAndStartProcess(dir: File, headless: Boolean, javaArgs: List<String>, jvmArgs: List<String>): Process? {
        if (!File(dir, jarName).exists()) {
            return null
        }
        if (!File(dir, "node.conf").let { it.exists() && acceptNodeConf(it) }) {
            return null
        }
        val debugPort = debugPortAlloc.next()
        val monitoringPort = monitoringPortAlloc.next()
        println("Starting $jarName in $dir on debug port $debugPort")
        val process = (if (headless) ::HeadlessJavaCommand else ::TerminalWindowJavaCommand)(this, dir, debugPort, monitoringPort, javaArgs, jvmArgs).start()
        if (os == OS.MACOS) Thread.sleep(1000)
        return process
    }

    internal abstract val headlessArgs: List<String>
}

private object NodeJarType : JarType("corda.jar", jolokiaLogHandler = "net.corda.node.JolokiaSlf4Adapter") {
    override fun acceptNodeConf(nodeConf: File) = true
    override val headlessArgs = listOf("--no-local-shell")
}

private object WebJarType : JarType(jarName = "corda-webserver.jar", jolokiaLogHandler = "net.corda.webserver.JolokiaSlf4Adapter") {
    // TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
    override fun acceptNodeConf(nodeConf: File) = Files.lines(nodeConf.toPath()).anyMatch { "webAddress" in it }
    override val headlessArgs = emptyList<String>()
}

private abstract class JavaCommand(
        jarName: String,
        internal val dir: File,
        debugPort: Int?,
        monitoringPort: Int?,
        internal val nodeName: String,
        args: List<String>,
        jvmArgs: List<String>,
        jolokiaLoggerClass: String?
) {
    private val jolokiaJar by lazy {
        File("$dir/drivers").listFiles { _, filename ->
            filename.matches("jolokia-jvm-.*-agent\\.jar$".toRegex())
        }.first().name
    }

    internal val command: List<String> = mutableListOf<String>().apply {
        add(getJavaPath())
        addAll(jvmArgs)
        add("-Dname=$nodeName")

        val jvmArgs: MutableList<String> = mutableListOf()
        null != debugPort && jvmArgs.add("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")

        if(null != monitoringPort) {
            val jolokiaConfig = java.lang.StringBuilder()
            jolokiaConfig.append("-javaagent:drivers/$jolokiaJar=port=$monitoringPort")
            if(jolokiaLoggerClass != null) {
                jolokiaConfig.append(",logHandlerClass=${jolokiaLoggerClass}")
            }
            jvmArgs.add(jolokiaConfig.toString())
        }
        if (jvmArgs.isNotEmpty()) {
            add("-Dcapsule.jvm.args=${jvmArgs.joinToString(separator = " ")}")
        }
        add("-jar")
        add(jarName)
        addAll(args)
    }

    internal abstract fun processBuilder(): ProcessBuilder
    internal fun start() = processBuilder().directory(dir).start()
    internal abstract fun getJavaPath(): String
}

private class HeadlessJavaCommand(jarType: JarType, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>)
    : JavaCommand(jarType.jarName, dir, debugPort, monitoringPort, dir.name, jarType.headlessArgs + args, jvmArgs, jarType.jolokiaLogHandler) {
    override fun processBuilder() = ProcessBuilder(command).redirectError(File("error.$nodeName.log")).inheritIO()
    override fun getJavaPath() = File(File(System.getProperty("java.home"), "bin"), "java").path
}

private class TerminalWindowJavaCommand(jarType: JarType, dir: File, debugPort: Int?, monitoringPort: Int?, args: List<String>, jvmArgs: List<String>)
    : JavaCommand(jarType.jarName, dir, debugPort, monitoringPort, "${dir.name}-${jarType.jarName}", args, jvmArgs, jarType.jolokiaLogHandler) {
    override fun processBuilder() = ProcessBuilder(when (os) {
        OS.MACOS -> {
            listOf("osascript", "-e", """tell app "Terminal"
    activate
    delay 0.5
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd \"$dir\" ; \"${command.joinToString("""\" \"""")}\" && exit'" in selected tab of the front window
end tell""")
        }
        OS.WINDOWS -> {
            listOf("cmd", "/C", "start ${command.joinToString(" ") { windowsSpaceEscape(it) }}")
        }
        OS.LINUX -> {
            // Start shell to keep window open unless java terminated normally or due to SIGTERM:
            val command = "${unixCommand()}; [ $? -eq 0 -o $? -eq 143 ] || sh"
            if (isTmux()) {
                listOf("tmux", "new-window", "-n", nodeName, command)
            } else {
                listOf("xterm", "-T", nodeName, "-e", command)
            }
        }
    })

    private fun unixCommand() = command.map(::quotedFormOf).joinToString(" ")
    override fun getJavaPath(): String = File(File(System.getProperty("java.home"), "bin"), "java").path

    // Replace below is to fix an issue with spaces in paths on Windows.
    // Quoting the entire path does not work, only the space or directory within the path.
    private fun windowsSpaceEscape(s:String) = s.replace(" ", "\" \"")
}

private fun quotedFormOf(text: String) = "'${text.replace("'", "'\\''")}'" // Suitable for UNIX shells.
private fun isTmux() = System.getenv("TMUX")?.isNotEmpty() ?: false
