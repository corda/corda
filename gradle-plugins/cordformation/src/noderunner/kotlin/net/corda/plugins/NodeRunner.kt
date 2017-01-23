package net.corda.plugins

import java.awt.GraphicsEnvironment
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale



fun main(args: Array<String>) {
    val headless = (GraphicsEnvironment.isHeadless() || (!args.isEmpty() && (args[0] == "--headless")))
    NodeRunner(headless).run()
}

class NodeRunner(val headless: Boolean) {
    private companion object {
        val jarName = "corda.jar"
        val nodeConfName = "node.conf"
    }

    private val startedProcesses = mutableListOf<Process>()

    fun run() {
        val workingDir = Paths.get(System.getProperty("user.dir")).toFile()
        println("Starting node runner in $workingDir")

        workingDir.list().map { File(workingDir, it) }.forEach {
            if (isNode(it)) {
                startNode(it)
            }

            if (isWebserver(it)) {
                startWebServer(it)
            }
        }

        println("Started ${startedProcesses.size} processes")
        println("Node runner finished")
    }

    private fun isNode(maybeNodeDir: File) = maybeNodeDir.isDirectory
            && File(maybeNodeDir, jarName).exists()
            && File(maybeNodeDir, nodeConfName).exists()

    private fun isWebserver(maybeWebserverDir: File) = isNode(maybeWebserverDir) && hasWebserverPort(maybeWebserverDir)

    // TODO: Add a webserver.conf, or use TypeSafe config instead of this hack
    private fun hasWebserverPort(nodeConfDir: File) = Files.readAllLines(File(nodeConfDir, nodeConfName).toPath()).joinToString { it }.contains("webAddress")

    private fun startNode(nodeDir: File) {
        println("Starting node in $nodeDir")
        startedProcesses.add(startCorda(nodeDir))
    }

    private fun startWebServer(webserverDir: File) {
        println("Starting webserver in $webserverDir")
        startedProcesses.add(startCorda(webserverDir, listOf("--webserver")))
    }

    private fun startCorda(dir: File, args: List<String> = listOf()): Process {
        return if (headless) {
            execCordaJar(dir, args)
        } else {
            execCordaInTerminalWindow(dir, args)
        }
    }

    private fun execCordaJar(dir: File, args: List<String> = listOf()): Process {
        val nodeName = dir.toPath().fileName
        val separator = System.getProperty("file.separator")
        val path = System.getProperty("java.home") + separator + "bin" + separator + "java"
        val builder = ProcessBuilder(listOf(path, "-Dname=$nodeName", "-jar", jarName) + args)
        builder.redirectError(Paths.get("error.${dir.toPath().fileName}.log").toFile())
        builder.inheritIO()
        builder.directory(dir)
        return builder.start()
    }

    private fun execCordaInTerminalWindow(dir: File, args: List<String> = listOf()): Process {
        val javaCmd = "java -jar $jarName " + args.joinToString(" ") { it }
        val nodeName = dir.toPath().fileName
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
}