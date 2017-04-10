package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

private val nodeJarName = "corda.jar"
private val webJarName = "corda-webserver.jar"
private val nodeConfName = "node.conf"
private val HEADLESS_FLAG = "--headless"

private val os: OS by lazy {
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
    if ((osName.indexOf("mac") >= 0) || (osName.indexOf("darwin") >= 0)) OS.MACOS
    else if (osName.indexOf("win") >= 0) OS.WINDOWS
    else OS.LINUX
}

private enum class OS { MACOS, WINDOWS, LINUX }

data class IncrementalPortAllocator(var basePort: Int = 5005) {
    fun next(): Int = basePort++
}

val debugPortAlloc: IncrementalPortAllocator = IncrementalPortAllocator()

fun main(args: Array<String>) {
    val startedProcesses = mutableListOf<Process>()
    val headless = GraphicsEnvironment.isHeadless() || (args.isNotEmpty() && args[0] == HEADLESS_FLAG)
    val workingDir = Paths.get(System.getProperty("user.dir")).toFile()
    val javaArgs = args.filter { it != HEADLESS_FLAG }
    println("Starting nodes in $workingDir")

    workingDir.list().map { File(workingDir, it) }.forEach {
        if (isNode(it)) {
            startedProcesses += startJarProcess(headless, it, nodeJarName, javaArgs)
        }

        if (isWebserver(it)) {
            startedProcesses += startJarProcess(headless, it, webJarName, javaArgs)
        }
    }

    println("Started ${startedProcesses.size} processes")
    println("Finished starting nodes")
}

private fun startJarProcess(headless: Boolean, dir: File, jarName: String, javaArgs: List<String>) : Process {
    val runJar = getJarRunner(headless)
    val debugPort = debugPortAlloc.next()
    println("Starting $jarName in $dir on debug port $debugPort")
    val proc = runJar(jarName, dir, javaArgs, debugPort)
    if (os == OS.MACOS) Thread.sleep(1000)
    return proc
}

private fun isNode(maybeNodeDir: File) = maybeNodeDir.isDirectory
        && File(maybeNodeDir, nodeJarName).exists()
        && File(maybeNodeDir, nodeConfName).exists()

private fun isWebserver(maybeWebserverDir: File) = maybeWebserverDir.isDirectory
        && File(maybeWebserverDir, webJarName).exists()
        && File(maybeWebserverDir, nodeConfName).exists()
        && hasWebserverPort(maybeWebserverDir)

// TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
private fun hasWebserverPort(nodeConfDir: File) = Files.readAllLines(File(nodeConfDir, nodeConfName).toPath()).joinToString { it }.contains("webAddress")

private fun getDebugPortArg(debugPort: Int?) = if (debugPort != null) {
    listOf("-Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$debugPort")
} else {
    emptyList()
}

private fun getJarRunner(headless: Boolean): (String, File, List<String>, Int?) -> Process = if (headless) ::execJar else ::execJarInTerminalWindow

private fun execJar(jarName: String, dir: File, args: List<String> = listOf(), debugPort: Int?): Process {
    val nodeName = dir.toPath().fileName
    val separator = System.getProperty("file.separator")
    val path = System.getProperty("java.home") + separator + "bin" + separator + "java"
    val builder = ProcessBuilder(listOf(path, "-Dname=$nodeName") + getDebugPortArg(debugPort) + listOf("-jar", jarName, "--no-local-shell") + args)
    builder.redirectError(Paths.get("error.${dir.toPath().fileName}.log").toFile())
    builder.inheritIO()
    builder.directory(dir)
    return builder.start()
}

private fun execJarInTerminalWindow(jarName: String, dir: File, args: List<String> = listOf(), debugPort: Int?): Process {
    val nodeName = "${dir.toPath().fileName}-$jarName"
    val javaCmd = (listOf("java", "-Dname=$nodeName") + getDebugPortArg(debugPort) + listOf("-jar", jarName) + args).joinToString(" ") { it }
    val builder = when (os) {
        OS.MACOS -> ProcessBuilder(
                "osascript", "-e",
                """tell app "Terminal"
    activate
    tell app "System Events" to tell process "Terminal" to keystroke "t" using command down
    delay 0.5
    do script "bash -c 'cd $dir; /usr/libexec/java_home -v 1.8 --exec $javaCmd && exit'" in selected tab of the front window
end tell"""
        )
        OS.WINDOWS -> ProcessBuilder(
                "cmd"
                , "/C", "start $javaCmd"
        )
        OS.LINUX -> {
            val isTmux = System.getenv("TMUX")?.isNotEmpty() ?: false
            if (isTmux) {
                ProcessBuilder(
                        "tmux", "new-window", "-n", nodeName, javaCmd
                )
            } else {
                ProcessBuilder(
                        "xterm", "-T", nodeName, "-e", javaCmd
                )
            }
        }
    }
    return builder.directory(dir).start()
}
