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

fun main(args: Array<String>) {
    val startedProcesses = mutableListOf<Process>()
    val headless = (GraphicsEnvironment.isHeadless() || (!args.isEmpty() && (args[0] == HEADLESS_FLAG)))
    val runJar = getJarRunner(headless)
    val workingDir = Paths.get(System.getProperty("user.dir")).toFile()
    val javaArgs = args.filter { it != HEADLESS_FLAG }
    println("Starting nodes in $workingDir")

    workingDir.list().map { File(workingDir, it) }.forEach {
        if (isNode(it)) {
            println("Starting node in $it")
            startedProcesses.add(runJar(nodeJarName, it, javaArgs))
            if (os == OS.MACOS) Thread.sleep(1000)
        }

        if (isWebserver(it)) {
            println("Starting webserver in $it")
            startedProcesses.add(runJar(webJarName, it, javaArgs))
            if (os == OS.MACOS) Thread.sleep(1000)
        }
    }

    println("Started ${startedProcesses.size} processes")
    println("Finished starting nodes")
}

private fun isNode(maybeNodeDir: File) = maybeNodeDir.isDirectory
        && File(maybeNodeDir, nodeJarName).exists()
        && File(maybeNodeDir, webJarName).exists()
        && File(maybeNodeDir, nodeConfName).exists()

private fun isWebserver(maybeWebserverDir: File) = isNode(maybeWebserverDir) && hasWebserverPort(maybeWebserverDir)

// TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
private fun hasWebserverPort(nodeConfDir: File) = Files.readAllLines(File(nodeConfDir, nodeConfName).toPath()).joinToString { it }.contains("webAddress")

private fun getJarRunner(headless: Boolean): (String, File, List<String>) -> Process = if (headless) ::execJar else ::execJarInTerminalWindow

private fun execJar(jarName: String, dir: File, args: List<String> = listOf()): Process {
    val nodeName = dir.toPath().fileName
    val separator = System.getProperty("file.separator")
    val path = System.getProperty("java.home") + separator + "bin" + separator + "java"
    val builder = ProcessBuilder(listOf(path, "-Dname=$nodeName", "-jar", jarName) + args)
    builder.redirectError(Paths.get("error.${dir.toPath().fileName}.log").toFile())
    builder.inheritIO()
    builder.directory(dir)
    return builder.start()
}

private fun execJarInTerminalWindow(jarName: String, dir: File, args: List<String> = listOf()): Process {
    val javaCmd = "java -jar $jarName " + args.joinToString(" ") { it }
    val nodeName = "${dir.toPath().fileName} $jarName"
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
