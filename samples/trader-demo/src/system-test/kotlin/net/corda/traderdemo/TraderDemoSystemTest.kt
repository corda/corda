package net.corda.traderdemo

import net.sf.expectit.Expect
import net.sf.expectit.ExpectBuilder
import net.sf.expectit.Result
import net.sf.expectit.matcher.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.*
import java.lang.UnsupportedOperationException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.TimeUnit
import javax.naming.ConfigurationException

class TraderDemoSystemTest {

    private var bankA: NodeProcess? = null
    private var bankB: NodeProcess? = null
    private var bankOfCorda: NodeProcess? = null
    private var notaryService: NodeProcess? = null

    @Before
    fun startNodes() {
        bankA = startNode("BankA")
        bankB = startNode("BankB")
        bankOfCorda = startNode("BankOfCorda")
        notaryService = startNode("NotaryService")
    }

    @After
    fun stopNodes() {
        bankA?.close()
        bankB?.close()
        bankOfCorda?.close()
        notaryService?.close()
    }

    @Test
    fun `runs trader demo system test`() {


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

    private fun startNode(nodeDirectoryName: String): NodeProcess {
        return NodeStarter().start(File(nodesDirectory, nodeDirectoryName))
    }

    private val nodesDirectory by lazy {
        Paths
            .get(System.getProperty("user.dir"), "build", "nodes")
            .toFile()
    }
}

private abstract class JarType(private val _jarName: String) {

    fun jarName(): String = _jarName

    open fun validateFilesOrThrow(nodeDirectory: File) {
        throw NotImplementedError()
    }

    protected fun validateFilesExistOrThrow(nodeDirectory: File) {
        nodeDirectory.existsOrThrow()
        jarFile(nodeDirectory).existsOrThrow()
        configFile(nodeDirectory).existsOrThrow()
    }

    protected fun jarFile(nodeDirectory: File) = File(nodeDirectory, jarName())
    protected fun configFile(nodeDirectory: File) = File(nodeDirectory, "node.conf")
}

private object cordaJarType: JarType("corda.jar") {

    override fun validateFilesOrThrow(nodeDirectory: File) = validateFilesExistOrThrow(nodeDirectory)

}

private object webserverJarType: JarType("corda-webserver.jar") {

    override fun validateFilesOrThrow(nodeDirectory: File) {
        validateFilesExistOrThrow(nodeDirectory)
        validateConfigOrThrow(configFile(nodeDirectory))
    }

    private fun validateConfigOrThrow(configFile: File) {
        val isConfigValid = Files
                .lines(configFile.toPath())
                .anyMatch { "webAddress" in it }
        if (!isConfigValid) {
            throw ConfigurationException("Node config does not contain webAddress.")
        }
    }
}

private class NodeStarter {

    fun start(
            nodeDirectory: File,
            jarType: JarType = cordaJarType,
            isHeadless: Boolean = false,
            isCapsuleDebugOn: Boolean = false,
            jarArguments: List<String> = emptyList()
    ): NodeProcess {
        jarType.validateFilesOrThrow(nodeDirectory)
        val jarFile = File(nodeDirectory, jarType.jarName())
        val nodeCommand = buildNodeCommand(jarFile, isHeadless, isCapsuleDebugOn, jarArguments)
        return NodeProcessCreator().create(nodeCommand, nodeDirectory)
    }

    private fun buildNodeCommand(
            jarFile: File,
            isHeadless: Boolean,
            isCapsuleDebugOn: Boolean,
            jarArguments: List<String>
    ): List<String> {
        return NodeCommandBuilder().run {
            withJarFile(jarFile)
            if (isHeadless) {
                withHeadlessFlag()
            }
            if (isCapsuleDebugOn) {
                withCapsuleDebugOn()
            }
            withJarArguments(jarArguments)
            build()
        }
    }
}

private class NodeCommandBuilder {

    private var _jarFile: File? = null
    private var _isHeadless: Boolean? = null
    private var _isCapsuleDebugOn: Boolean? = null
    private var _jarArguments: List<String>? = null

    fun withJarFile(jarFile: File): NodeCommandBuilder {
        _jarFile.expectNullOrThrow("JarFile")
        _jarFile = jarFile
        return this
    }

    fun withJarArguments(jarArguments: List<String>): NodeCommandBuilder {
        _jarArguments.expectNullOrThrow("JarArguments")
        _jarArguments = jarArguments
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
        validateJarFileNameOrThrow()
        return buildShellCommand()
    }

    private fun buildShellCommand(): List<String> {
        return ShellCommandBuilder()
            .withCommand(buildJavaCommand())
            .withWorkingDirectory(_jarFile!!.parentFile)
            .build()
    }

    private fun buildJavaCommand(): List<String> {
        return JavaCommandBuilder()
            .withJavaArguments(javaArguments())
            .withJarFile(_jarFile!!)
            .withJarArguments(jarArguments())
            .build()
    }

    private fun validateJarFileNameOrThrow() {
        val validJarFileNames = listOf(cordaJarType.jarName(), webserverJarType.jarName())
        if (_jarFile!!.name !in validJarFileNames) {
            throw IllegalArgumentException("JarFile.name is not valid.")
        }
    }

    private fun javaArguments(): List<String> {
        return mutableListOf<String>().apply {
            if (_isCapsuleDebugOn == true) {
                add("-Dcapsule.log=verbose")
            }
            add(nodeName())
            add(debugPort())
        }
    }

    private fun nodeName(): String {
        return "-Dname=" + _jarFile!!.parentFile.name + if (_isHeadless == true) "" else "-${_jarFile!!.name}"
    }

    private fun debugPort(): String {
        val portNumber = debugPortNumberGenerator.next()
        return "-Dcapsule.jvm.args=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$portNumber"
    }

    private fun jarArguments(): List<String> {
        return mutableListOf<String>().apply {
            if (_isHeadless == true) {
                add("--no-local-shell")
            }
            addAll(_jarArguments ?: emptyList())
        }
    }
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
            TODO("Test this - Also apply workingDirectory")
            add("cmd")
            add("/C")
            add("start ${_command!!.joinToString(" ")}")
        }
    }

    private fun buildOsx(): List<String> {
        return mutableListOf<String>().apply {
            add("/bin/bash")
            add("-c")
            add("cd ${_workingDirectory!!.path} ; ${_command!!.joinToString(" ")} && exit")
        }
    }

    private fun buildLinux(): List<String> {
        return mutableListOf<String>().apply {
            TODO()
        }
    }
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
            add(javaPathString)
            addAll(_javaArguments ?: emptyList())
            add("-jar")
            add(_jarFile!!.name)
            addAll(_jarArguments ?: emptyList())
        }
    }

    private val javaPathString: String by lazy {
        Paths
            .get(System.getProperty("java.home"), "bin", "java")
            .toString()
    }
}

private class NodeProcessCreator {

    fun create(command: List<String>, workingDirectory: File): NodeProcess {
        val process = buildProcess(command, workingDirectory)
        val expect = buildExpect(process)
        return NodeProcess(process, expect)
    }

    private fun buildProcess(command: List<String>, workingDirectory: File): Process {
        return ProcessBuilder()
            .command(command)
            .directory(workingDirectory)
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

}

private class NodeProcess(private val _process: Process, private val _expect: Expect) {

    fun expectCordaLogo(): Result {
        val pattern =
            "?(m)" +
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

    fun expectPattern(pattern: String): Result {
        return _expect.expect(Matchers.regexp(pattern))
    }

    fun close() {
        _expect.close()
        _process
            .destroyForcibly()
            .waitFor(5, TimeUnit.SECONDS)
    }
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
    private var portNumber = 5005
    fun next() = portNumber++
}

private fun File.existsOrThrow() {
    if (!this.exists()) {
        throw FileNotFoundException("${this.path} does not exist.")
    }
}

private fun Any?.expectNullOrThrow(name: String = "Nullable") {
    this?.let { throw IllegalArgumentException("$name has already been set to a value.") }
}
