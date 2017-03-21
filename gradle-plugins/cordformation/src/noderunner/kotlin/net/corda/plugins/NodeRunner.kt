package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale

private val nodeJarName = "corda.jar"
private val webJarName = "corda-webserver.jar"
private val nodeConfName = "node.conf"

fun main(args: Array<String>) {
    val startedProcesses = mutableListOf<Process>()
    val headless = (GraphicsEnvironment.isHeadless() || (!args.isEmpty() && (args[0] == "--headless")))
    val runJar = getJarRunner(headless)
    val workingDir = Paths.get(System.getProperty("user.dir")).toFile()
    val javaArgs = listOf<String>() // TODO: Add args passthrough
    println("Starting node runner in $workingDir")

    workingDir.list().map { File(workingDir, it) }.forEach {
        if (isNode(it)) {
            println("Starting node in $it")
            startedProcesses.add(runJar(nodeJarName, it, javaArgs))
        }

        if (isWebserver(it)) {
            println("Starting webserver in $it")
            startedProcesses.add(runJar(webJarName, it, javaArgs))
        }
    }

    println("Started ${startedProcesses.size} processes")
    println("Node runner finished")
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
    val nodeName = dir.toPath().fileName + " " + jarName
    val osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH)
    val cmd = if ((osName.indexOf("mac") >= 0) || (osName.indexOf("darwin") >= 0)) {
        """osascript -e "tell app "Terminal
activate
tell application \"System Events\" to tell process \"Terminal\" to keystroke \"t\" using command down
delay 0.5
do script "bash -c 'cd $dir; /usr/libexec/java_home -v 1.8 --exec $javaCmd && exit'" in window 1"
"""
    } else if (osName.indexOf("win") >= 0) {
        """cmd /C "start $javaCmd""""
    } else {
        // Assume Linux
        """xterm -T "$nodeName" -e $javaCmd"""
    }

    return Runtime.getRuntime().exec(cmd, null, dir)
}