package net.corda.traderdemo

import net.sf.expectit.Expect
import net.sf.expectit.ExpectBuilder
import net.sf.expectit.Result
import net.sf.expectit.matcher.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.lang.UnsupportedOperationException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.naming.ConfigurationException

class TraderDemoSystemTest {

    private var _bankA: NodeExpectingProcess? = null
    private var _bankB: NodeExpectingProcess? = null
    private var _bankOfCorda: NodeExpectingProcess? = null
    private var _notaryService: NodeExpectingProcess? = null

    @Before
    fun startNodes(): Unit {
        _bankA = startCordaNode("BankA")
        _bankB = startCordaNode("BankB")
        _bankOfCorda = startCordaNode("BankOfCorda")
        _notaryService = startCordaNode("NotaryService")
    }

    @After
    fun stopNodes(): Unit {
        _bankA?.close()
        _bankB?.close()
        _bankOfCorda?.close()
        _notaryService?.close()
    }

    @Test
    fun `runs trader demo system test`(): Unit {


        Thread.sleep(5000)
//
//        var result = expect
//            .withTimeout(60, TimeUnit.SECONDS)
//            .expect(Matchers.regexp("(?m)^Node for \"Bank A\" started up and registered in .*"))
//
//        println(result!!.input
//
//        println(result.isSuccessful)
////        Node for "Bank A" started up and registered in 5.59 sec
////        Node for "Bank A" started up and registered in 5.59 sec

    }

    private fun startCordaNode(nodeDirectoryName: String): NodeExpectingProcess {
        return TraderDemoNode().start(nodeDirectoryName)
    }
}

private class TraderDemoNode {

    fun start(nodeDirectoryName: String): NodeExpectingProcess {
        val nodeDirectory: File = File(_nodesDirectory, nodeDirectoryName)
        val nodeFileInfo = NodeFileInfo(nodeDirectory, cordaJarType)
        nodeFileInfo.validateFilesOrThrow()
        val expectingProcess = startExpectingProcess(nodeFileInfo)
        return NodeExpectingProcess(expectingProcess)
    }

    private fun startExpectingProcess(nodeFileInfo: NodeFileInfo): ExpectingProcess {
        return ExpectingProcessBuilder()
                .withCommand(buildNodeCommand(nodeFileInfo))
                .withWorkingDirectory(nodeFileInfo.directory())
                .start()
    }

    private fun buildNodeCommand(nodeFileInfo: NodeFileInfo): List<String> {
        return NodeCommandBuilder()
                .withNodeFileInfo(nodeFileInfo)
                .build()
    }

    private val _nodesDirectory: File by lazy {
        Paths
                .get(System.getProperty("user.dir"), "build", "nodes")
                .toFile()
    }
}

private class NodeExpectingProcess(private val _expectingProcess: ExpectingProcess) {

    fun expectCordaLogo(): Result {
        val pattern = "?(m)" +
                """^\Q   ______               __ \E.*$\r\n?|\n""" +
                """^\Q  / ____/     _________/ /___ _\E.*$\r\n?|\n""" +
                """^\Q / /     __  / ___/ __  / __ `/\E.*$\r\n?|\n""" +
                """^\Q/ /___  /_/ / /  / /_/ / /_/ /\E.*$\r\n?|\n""" +
                """^\Q\____/     /_/   \__,_/\__,_/\E.*$\r\n?|\n"""
        return expectPattern(pattern)
    }

    fun expectLogsLocation(path: String): Result {
        return expectPattern("(?m)^Logs can be found in +: $path$")
    }

    fun expectDatabaseConnectionUrl(url: String): Result {
        return expectPattern("(?m)^Database connection url is +: $url$")
    }

    fun expectIncomingConnectionAddress(host: String, portNumber: Int): Result {
        return expectPattern("(?m)^Incoming connection address +: $host:$portNumber")
    }

    fun expectListeningOnPort(portNumber: Int): Result {
        return expectPattern("(?m)^Listening on port +: $portNumber$")
    }

    fun expectLoadedCorDapps(cordapps: List<String>): Result {
        return expectPattern("(?m)^Loaded CorDapps +: ${cordapps.joinToString(", ")}$")
    }

    fun expectStartedUp(nodeName: String): Result {
        return expectPattern("(?m)^Node for \"$nodeName\" started up and registered in \\d+(\\.\\d+)? sec$")
    }

    fun close(): Unit = _expectingProcess.close()

    private fun expectPattern(pattern: String): Result = _expectingProcess.expectPattern(pattern)
}

private class ExpectingProcess(private val _process: Process, private val _expect: Expect) {

    fun expectPattern(pattern: String): Result {
        return _expect.expect(Matchers.regexp(pattern))
    }

    fun close(): Unit {
        _expect.close()
        _process
                .destroyForcibly()
                .waitFor(5, TimeUnit.SECONDS)
    }
}

private class ExpectingProcessBuilder {

    private var _command: List<String>? = null
    private var _workingDirectory: File? = null

    fun withCommand(command: List<String>): ExpectingProcessBuilder {
        _command.expectNullOrThrow("Command")
        _command = command
        return this
    }

    fun withWorkingDirectory(workingDirectory: File): ExpectingProcessBuilder {
        _workingDirectory.expectNullOrThrow("WorkingDirectory")
        _workingDirectory = workingDirectory
        return this
    }

    fun start(): ExpectingProcess {
        val process = buildProcess()
        val expect = buildExpect(process)
        return ExpectingProcess(process, expect)
    }

    private fun buildProcess(): Process {
        return ProcessBuilder()
                .command(command())
                .directory(workingDirectory())
                .redirectErrorStream(true)
                .start()
    }

    private fun buildExpect(process: Process): Expect {
        return ExpectBuilder()
                .withInputs(process.inputStream)
                .withOutput(process.outputStream)
//            .withExceptionOnFailure() TODO uncomment when working
                .build()
    }

    private fun command(): List<String> = _command!!
    private fun workingDirectory(): File = _workingDirectory!!
}

private class NodeFileInfo(private val _nodeDirectory: File, private val _jarType: JarType) {

    fun validateFilesOrThrow(): Unit = _jarType.validateFilesOrThrow(_nodeDirectory)
    fun directory(): File = _nodeDirectory
    fun directoryName(): String = _nodeDirectory.name
    fun jarFile(): File = File(_nodeDirectory, jarName())
    fun jarName(): String = _jarType.jarName()
}

private abstract class JarType(private val _jarName: String) {

    fun jarName(): String = _jarName

    open fun validateFilesOrThrow(nodeDirectory: File): Unit {
        throw NotImplementedError()
    }

    protected fun validateFilesExistOrThrow(nodeDirectory: File): Unit {
        nodeDirectory.existsOrThrow()
        jarFile(nodeDirectory).existsOrThrow()
        configFile(nodeDirectory).existsOrThrow()
    }

    protected fun jarFile(nodeDirectory: File): File = File(nodeDirectory, jarName())
    protected fun configFile(nodeDirectory: File): File = File(nodeDirectory, "node.conf")
}

private object cordaJarType: JarType("corda.jar") {

    override fun validateFilesOrThrow(nodeDirectory: File): Unit = validateFilesExistOrThrow(nodeDirectory)
}

private object webserverJarType: JarType("corda-webserver.jar") {

    override fun validateFilesOrThrow(nodeDirectory: File): Unit {
        validateFilesExistOrThrow(nodeDirectory)
        validateConfigOrThrow(configFile(nodeDirectory))
    }

    private fun validateConfigOrThrow(configFile: File): Unit {
        val isConfigValid = Files
                .lines(configFile.toPath())
                .anyMatch { "webAddress" in it }
        if (!isConfigValid) {
            throw ConfigurationException("Node config does not contain webAddress.")
        }
    }
}

private class NodeCommandBuilder {

    private var _nodeFileInfo: NodeFileInfo? = null
    private var _arguments: List<String>? = null
    private var _isHeadless: Boolean? = null
    private var _isCapsuleDebugOn: Boolean? = null

    fun withNodeFileInfo(nodeFileInfo: NodeFileInfo): NodeCommandBuilder {
        _nodeFileInfo.expectNullOrThrow("NodeDirectory")
        _nodeFileInfo = nodeFileInfo
        return this
    }

    fun withArguments(arguments: List<String>): NodeCommandBuilder {
        _arguments.expectNullOrThrow("Arguments")
        _arguments = arguments
        return this
    }

    fun withHeadlessFlag(): NodeCommandBuilder {
        _isHeadless.expectNullOrThrow("Headless")
        _isHeadless = true
        return this
    }

    fun withCapsuleDebugOn(): NodeCommandBuilder {
        _isCapsuleDebugOn.expectNullOrThrow("CapsuleDebugOn")
        _isCapsuleDebugOn = true
        return this
    }

    fun build(): List<String> {
        return ShellCommandBuilder()
                .withCommand(buildJavaCommand())
                .withWorkingDirectory(directory())
                .build()
    }

    private fun buildJavaCommand(): List<String> {
        return JavaCommandBuilder()
                .withJavaArguments(buildJavaArguments())
                .withJarFile(jarFile())
                .withJarArguments(buildJarArguments())
                .build()
    }

    private fun buildJavaArguments(): List<String> {
        return mutableListOf<String>().apply {
            if (isCapsuleDebugOn()) {
                add("-Dcapsule.log=verbose")
            }
            add(nodeName())
            add(debugPort())
        }
    }

    private fun nodeName(): String {
        return "-Dname=${directoryName()}" + if (isHeadless()) "" else "-${jarName()}"
    }

    private fun debugPort(): String {
        val portNumber = debugPortNumberGenerator.next()
        return "-Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$portNumber"
    }

    private fun buildJarArguments(): List<String> {
        return mutableListOf<String>().apply {
            if (isHeadless()) {
                add("--no-local-shell")
            }
            addAll(arguments())
        }
    }

    private fun directory(): File = _nodeFileInfo!!.directory()
    private fun directoryName(): String = _nodeFileInfo!!.directoryName()
    private fun jarFile(): File = _nodeFileInfo!!.jarFile()
    private fun jarName(): String = _nodeFileInfo!!.jarName()
    private fun arguments(): List<String> = _arguments ?: emptyList()
    private fun isHeadless(): Boolean = _isHeadless == true
    private fun isCapsuleDebugOn(): Boolean = _isCapsuleDebugOn == true
}

private class JavaCommandBuilder {

    private var _javaArguments: List<String>? = null
    private var _jarFile: File? = null
    private var _jarArguments: List<String>? = null

    fun withJavaArguments(javaArguments: List<String>): JavaCommandBuilder {
        _javaArguments.expectNullOrThrow("JavaArguments")
        _javaArguments = javaArguments
        return this
    }

    fun withJarFile(jarFile: File): JavaCommandBuilder {
        _jarFile.expectNullOrThrow("JarFile")
        _jarFile = jarFile
        return this
    }

    fun withJarArguments(jarArguments: List<String>): JavaCommandBuilder {
        _jarArguments.expectNullOrThrow("JarArguments")
        _jarArguments = jarArguments
        return this
    }

    fun build(): List<String> {
        return mutableListOf<String>().apply {
            add(_javaPathString)
            addAll(javaArguments())
            add("-jar")
            add(jarName())
            addAll(jarArguments())
        }
    }

    private val _javaPathString: String by lazy {
        Paths
                .get(System.getProperty("java.home"), "bin", "java")
                .toString()
    }

    private fun javaArguments(): List<String> = _javaArguments ?: emptyList()
    private fun jarName(): String = _jarFile!!.name
    private fun jarArguments(): List<String> = _jarArguments ?: emptyList()
}

private class ShellCommandBuilder {

    private var _command: List<String>? = null
    private var _workingDirectory: File? = null

    fun withCommand(command: List<String>): ShellCommandBuilder {
        _command.expectNullOrThrow("Command")
        _command = command
        return this
    }

    fun withWorkingDirectory(workingDirectory: File): ShellCommandBuilder {
        _workingDirectory.expectNullOrThrow("WorkingDirectory")
        _workingDirectory = workingDirectory
        return this
    }

    fun build(): List<String> {
        return when (os) {
            OS.WINDOWS -> buildWindows()
            OS.OSX -> buildOsx()
            OS.LINUX -> buildLinux()
        }
    }

    private fun buildWindows(): List<String> {
        return mutableListOf<String>().apply {
            TODO() // TODO test this and also apply working directory
            add("cmd")
            add("/C")
            add("start ${commandString()}")
        }
    }

    private fun buildOsx(): List<String> {
        return mutableListOf<String>().apply {
            add("/bin/bash")
            add("-c")
            add("cd ${workingDirectoryPathString()} ; ${commandString()} && exit")
        }
    }

    private fun buildLinux(): List<String> {
        return mutableListOf<String>().apply {
            TODO() // TODO Implement this
        }
    }

    private fun commandString(): String = _command!!.joinToString(" ")
    private fun workingDirectoryPathString(): String = _workingDirectory!!.path
}

private enum class OS {
    WINDOWS,
    OSX,
    LINUX,
}

private val os: OS by lazy {
    val osName = System
            .getProperty("os.name", "generic")
            .toLowerCase(Locale.ENGLISH)
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

    private var _portNumber: Int = 5005

    fun next(): Int = _portNumber++
}

private fun File.existsOrThrow(): Unit {
    if (!this.exists()) {
        throw FileNotFoundException("${this.path} does not exist.")
    }
}

private fun Any?.expectNullOrThrow(name: String = "Nullable"): Unit {
    this?.let { throw IllegalArgumentException("$name has already been set to a value.") }
}
