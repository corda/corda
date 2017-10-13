package net.corda.traderdemo

import net.sf.expectit.Expect
import net.sf.expectit.ExpectBuilder
import net.sf.expectit.Result
import net.sf.expectit.matcher.Matchers
import org.junit.Test
import java.io.*
import java.lang.UnsupportedOperationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern
import javax.naming.ConfigurationException

class TraderDemoSystemTest {
    @Test
    fun `runs trader demo system test`() {
        var node = Node(workingDirectory, "BankA")
        var expect = node.launch(false, false, emptyList())

        var result = expect
            .withTimeout(60, TimeUnit.SECONDS)
            .expect(Matchers.regexp("(?m)^Node for \"Bank A\" started up and registered in .*"))

        println(result!!.input)

        println(result.isSuccessful)
//        Node for "Bank A" started up and registered in 5.59 sec
//        Node for "Bank A" started up and registered in 5.59 sec

        expect.close()

        println("asdga")
    }

    private val nodeSubDirectoryNames =
        listOf<String>("Notary Service", "Bank A", "Bank B", "BankOfCorda")

    private val workingDirectory by lazy {
        Paths
            .get(System.getProperty("user.dir"), "build", "nodes")
            .toFile()
    }
}

private class NodeProcess(private val process: Process) {



}

private class Node(private val workingDirectory: File, private val subDirectoryName: String) {

    fun launch(isHeadless: Boolean, isCapsuleDebugOn: Boolean, arguments: List<String>): Expect {
        validateFilesExistOrThrow()
        // TODO need to save process - pass into NodeProcess class
        val process = startProcess(isHeadless, isCapsuleDebugOn, arguments)
        return buildExpect(process)
    }

    private fun buildExpect(process: Process) =
        ExpectBuilder()
            .withInputs(process.inputStream)
            .withOutput(process.outputStream)
//            .withExceptionOnFailure() TODO uncomment when working
            .build()

    private fun startProcess(isHeadless: Boolean, isCapsuleDebugOn: Boolean, arguments: List<String>) =
        ProcessBuilder()
            .command(shellCommand(javaCommand(isHeadless, isCapsuleDebugOn, arguments)))
            .directory(subDirectory())
            .redirectErrorStream(true)
            .start()

    private fun shellCommand(javaCommand: List<String>) =
        ShellCommandBuilder().run {
            setCommand(javaCommand)
            setWorkingDirectory(subDirectory())
            build()
        }

    private fun javaCommand(isHeadless: Boolean, isCapsuleDebugOn: Boolean, arguments: List<String>) =
        JavaCommandBuilder().run {
            setJarFile(jarFile())
            if (isHeadless) {
                runHeadless()
            }
            if (isCapsuleDebugOn) {
                runWithCapsuleDebugOn()
            }
            arguments.forEach { addArgument(it) }
            build()
        }

    private fun validateFilesExistOrThrow() {
        subDirectory().existsOrThrow()
        jarFile().existsOrThrow()
        configFile().existsOrThrow()
    }

    private fun jarFile() =
        File(subDirectory(), "corda.jar")

    private fun configFile() =
        File(subDirectory(), "node.conf")

    private fun subDirectory() =
        File(workingDirectory, subDirectoryName)
}

private class ShellCommandBuilder {

    private var command: List<String> = mutableListOf()
    private var workingDirectory: File? = null

    fun setCommand(command: List<String>) {
        this.command = command
    }

    fun setWorkingDirectory(workingDirectory: File) {
        this.workingDirectory = workingDirectory
    }

    fun build() =
        when (os) {
            OS.WINDOWS -> buildWindows()
            OS.OSX -> buildOsx()
            OS.LINUX -> buildLinux()
        }

    private fun buildWindows(): List<String> =
        mutableListOf<String>().apply {
            TODO("Test this")
            add("cmd")
            add("/C")
            add("start ${command.joinToString(" ")}")
        }

    private fun buildOsx(): List<String> =
        mutableListOf<String>().apply {
            add("/bin/bash")
            add("-c")
            add("cd $workingDirectory ; ${command.joinToString(" ")} && exit")
        }

    private fun buildLinux(): List<String> =
        mutableListOf<String>().apply {
            TODO()
        }
}

private class JavaCommandBuilder {

    private var jarFile: File? = null
    private var isHeadless = false
    private var isCapsuleDebugOn = false
    private var arguments = mutableListOf<String>()

    fun setJarFile(jarFile: File) {
        this.jarFile = jarFile
    }

    fun runHeadless() {
        isHeadless = true
    }

    fun runWithCapsuleDebugOn() {
        isCapsuleDebugOn = true
    }

    fun addArgument(argument: String) {
        arguments.add(argument)
    }

    fun build(): List<String> =
        mutableListOf<String>().apply {
            add(javaPathString)
            addAll(javaOptions())
            add("-jar")
            add(jarFile!!.name)
            addAll(jarArguments())
        }

    private val javaPathString by lazy {
        Paths
            .get(System.getProperty("java.home"), "bin", "java")
            .toString()
    }

    private fun javaOptions(): List<String> =
        mutableListOf<String>().apply {
            if (isCapsuleDebugOn) {
                add("-Dcapsule.log=verbose")
            }
            add(nodeNameSystemProperty())
            add(debugPort())
        }

    private fun nodeNameSystemProperty() =
        "-Dname=" + jarFile!!.parentFile.name + if (isHeadless) "" else "-${jarFile!!.name}"

    private fun debugPort(): String {
        val portNumber = debugPortNumberGenerator.next()
        return "-Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$portNumber"
    }

    private fun jarArguments() =
        mutableListOf<String>().apply {
            if (isHeadless) {
                add("--no-local-shell")
            }
            addAll(arguments)
        }
}

private enum class OS {
    WINDOWS,
    OSX,
    LINUX
}

private val os by lazy {
    val osNameProperty = System.getProperty("os.name", "generic")
    var osName = osNameProperty.toLowerCase(Locale.ENGLISH)
    if ("mac" in osName || "darwin" in osName)
        OS.OSX
    else if ("win" in osName)
        OS.WINDOWS
    else if ("nix" in osName || "nux" in osName)
        OS.LINUX
    else
        throw UnsupportedOperationException("OS not supported.")
}

private object debugPortNumberGenerator {
    private var portNumber = 5005
    fun next() = portNumber++
}

private fun File.existsOrThrow() {
    if (!this.exists()) {
        throw FileNotFoundException("${this.path} does not exist.")
    }
}
