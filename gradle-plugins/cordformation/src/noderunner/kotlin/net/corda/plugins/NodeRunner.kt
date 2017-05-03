package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.util.*

private val HEADLESS_FLAG = "--headless"

private val os: OS by lazy {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
    if ((osName.indexOf("mac") >= 0) || (osName.indexOf("darwin") >= 0)) OS.MACOS
    else if (osName.indexOf("win") >= 0) OS.WINDOWS
    else OS.LINUX
}

private enum class OS { MACOS, WINDOWS, LINUX }

private object debugPortAlloc {
    private var basePort = 5005
    internal fun next() = basePort++
}

fun main(args: Array<String>) {
    val startedProcesses = mutableListOf<Process>()
    val headless = GraphicsEnvironment.isHeadless() || (args.isNotEmpty() && args[0] == HEADLESS_FLAG)
    val workingDir = File(System.getProperty("user.dir"))
    val javaArgs = args.filter { it != HEADLESS_FLAG }
    println("Starting nodes in $workingDir")
    workingDir.listFiles { file -> file.isDirectory }.forEach { dir ->
        listOf(NodeJar, WebJar).forEach { jar ->
            jar.maybeStartProcess(headless, dir, javaArgs)?.let { startedProcesses += it }
        }
    }
    println("Started ${startedProcesses.size} processes")
    println("Finished starting nodes")
}

private abstract class Jar(private val jarName: String) {
    internal abstract fun acceptNodeConf(nodeConf: File): Boolean
    internal fun maybeStartProcess(headless: Boolean, dir: File, javaArgs: List<String>): Process? {
        File(dir, jarName).exists() || return null
        File(dir, "node.conf").let { it.exists() && acceptNodeConf(it) } || return null
        val debugPort = debugPortAlloc.next()
        println("Starting $jarName in $dir on debug port $debugPort")
        val proc = (if (headless) ::execJar else ::execJarInTerminalWindow)(jarName, dir, javaArgs, debugPort)
        if (os == OS.MACOS) Thread.sleep(1000)
        return proc
    }
}

private object NodeJar : Jar("corda.jar") {
    override fun acceptNodeConf(nodeConf: File) = true
}

private object WebJar : Jar("corda-webserver.jar") {
    // TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
    override fun acceptNodeConf(nodeConf: File) = Files.lines(nodeConf.toPath()).anyMatch { "webAddress" in it }
}

private class JavaCommand(jarName: String, debugPort: Int?, nodeName: String, init: MutableList<String>.() -> Unit) {
    private val words = mutableListOf<String>().apply {
        add(File(File(System.getProperty("java.home"), "bin"), "java").path)
        add("-Dname=$nodeName")
        null != debugPort && add("-Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")
        add("-jar"); add(jarName)
        init()
    }

    internal fun processBuilder() = ProcessBuilder(words)
    internal fun joinToString() = words.joinToString(" ")
}

private fun execJar(jarName: String, dir: File, args: List<String>, debugPort: Int?) = run {
    val nodeName = dir.name
    JavaCommand(jarName, debugPort, nodeName) { add("--no-local-shell"); addAll(args) }.processBuilder().apply {
        redirectError(File("error.${nodeName}.log"))
        inheritIO()
        directory(dir)
    }.start()
}

private fun execJarInTerminalWindow(jarName: String, dir: File, args: List<String>, debugPort: Int?) = run {
    val nodeName = "${dir.name}-$jarName"
    val javaCmd = JavaCommand(jarName, debugPort, nodeName) { addAll(args) }.joinToString()
    ProcessBuilder(when (os) {
        OS.MACOS -> listOf(
                "osascript", "-e",
                """tell app "Terminal"
    activate
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd $dir; /usr/libexec/java_home -v 1.8 --exec $javaCmd && exit'" in selected tab of the front window
end tell"""
        )
        OS.WINDOWS -> listOf("cmd", "/C", "start $javaCmd")
        OS.LINUX -> {
            val isTmux = System.getenv("TMUX")?.isNotEmpty() ?: false
            "$javaCmd || sh".let { javaCmd ->
                if (isTmux) {
                    listOf("tmux", "new-window", "-n", nodeName, javaCmd)
                } else {
                    listOf("xterm", "-T", nodeName, "-e", javaCmd)
                }
            }
        }
    }).directory(dir).start()
}
