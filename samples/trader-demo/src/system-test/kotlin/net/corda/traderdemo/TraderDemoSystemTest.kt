package net.corda.traderdemo

import com.github.sgreben.regex_builder.FluentRe
import com.github.sgreben.regex_builder.Re
import net.corda.core.internal.checkNull
import net.corda.core.internal.existsOrThrow
import net.sf.expectit.Expect
import net.sf.expectit.ExpectBuilder
import net.sf.expectit.MultiResult
import net.sf.expectit.Result
import net.sf.expectit.interact.InteractBuilder
import net.sf.expectit.matcher.Matcher
import net.sf.expectit.matcher.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.lang.UnsupportedOperationException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.naming.ConfigurationException

class TraderDemoSystemTest {
    // Nullable types can be changed to lateinit and use .isInitialized from kotlin 1.2
    private var bankA: ExpectingProcess? = null
    private var bankB: ExpectingProcess? = null
    private var bankOfCorda: ExpectingProcess? = null
    private var notaryService: ExpectingProcess? = null
    @Before
    fun startNodes() {
        bankA = startCordaNode("BankA")
        bankB = startCordaNode("BankB")
        bankOfCorda = startCordaNode("BankOfCorda")
        notaryService = startCordaNode("NotaryService")
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


        


    }

    private fun startCordaNode(nodeDirectoryName: String): ExpectingProcess {
        TODO()
    }
}

private class TraderDemoNode {

    fun start(nodeDirectoryName: String): NodeExpectingProcess {
        val nodeDirectory: Path = nodesDirectory.resolve(nodeDirectoryName)
        val nodeFileInfo = NodeFileInfo(nodeDirectory, cordaJarType)
        nodeFileInfo.validateFilesOrThrow()
        val expectingProcess = startExpectingProcess(nodeFileInfo)
        return NodeExpectingProcess(expectingProcess)
    }

    private fun startExpectingProcess(nodeFileInfo: NodeFileInfo): ExpectingProcess {
        return ExpectingProcessBuilder(buildNodeCommand(nodeFileInfo), nodeFileInfo.directory()).start()
    }

    private fun buildNodeCommand(nodeFileInfo: NodeFileInfo): List<String> {
        return NodeCommandBuilder(nodeFileInfo).build()
    }

    private val nodesDirectory: Path by lazy {
        Paths.get(System.getProperty("user.dir"), "build", "nodes")
    }
}

private object cordaMatchers {
    fun logoMatcher(): Matcher<MultiResult> {
        return cordaLogoMatcher
    }

    fun logsLocationMatcher(path: String): Matcher<Result> {
        val pattern = FluentRe
                .match(Re.beginLine())
                .then(Re.string("Logs can be found in"))
                .then(Re.repeat(" "))
                .then(": ")
                .then(Re.string(path))
                .then(Re.endLine())
                .compile(Pattern.MULTILINE)
        return Matchers.regexp(pattern)
    }

    fun databaseConnectionUrlMatcher(url: String): Matcher<Result> {
        val pattern = FluentRe
                .match(Re.beginLine())
                .then(Re.string("Database connection url is"))
                .then(Re.repeat(" "))
                .then(": ")
                .then(Re.string(url))
                .then(Re.endLine())
                .compile(Pattern.MULTILINE)
        return Matchers.regexp(pattern)
    }

    fun incomingConnectionAddressMatcher(host: String, portNumber: Int): Matcher<Result> {
        val pattern = FluentRe
                .match(Re.beginLine())
                .then(Re.string("Incoming connection address"))
                .then(Re.repeat(" "))
                .then(": ")
                .then(Re.string(host))
                .then(":")
                .then(Re.string(portNumber.toString()))
                .then(Re.endLine())
                .compile(Pattern.MULTILINE)
        return Matchers.regexp(pattern)
    }

    fun listeningOnPortMatcher(portNumber: Int): Matcher<Result> {
        val pattern = FluentRe
                .match(Re.beginLine())
                .then(Re.string("Listening on port"))
                .then(Re.repeat(" "))
                .then(": ")
                .then(Re.string(portNumber.toString()))
                .then(Re.endLine())
                .compile(Pattern.MULTILINE)
        return Matchers.regexp(pattern)
    }

    fun loadedCorDappsMatcher(cordapps: List<String>): Matcher<Result> {
        val pattern = FluentRe
                .match(Re.beginLine())
                .then(Re.string("Loaded CorDapps"))
                .then(Re.repeat(" "))
                .then(": ")
                .then(Re.string(cordapps.joinToString(", ")))
                .then(Re.endLine())
                .compile(Pattern.MULTILINE)
        return Matchers.regexp(pattern)
    }

    fun startedUpMatcher(nodeName: String): Matcher<Result> {
        val pattern = FluentRe
                .match(Re.beginLine())
                .then(Re.string("""Node for "$nodeName" started up and registered in """))
                .then(Re.number())
                .then(FluentRe
                        .match(".")
                        .then(Re.number())
                        .optional()
                )
                .then(Re.string(" sec"))
                .then(Re.endLine())
                .compile(Pattern.MULTILINE)
        return Matchers.regexp(pattern)
    }
}

private object cordaLogoMatcher : Matcher<MultiResult> {
    override fun matches(input: String, isEof: Boolean): MultiResult {
        return logoMatcher.matches(input, isEof)
    }

    private val logoMatcher: Matcher<MultiResult> by lazy {
        Matchers.sequence(*logoMatchers())
    }

    private fun logoMatchers(): Array<Matcher<Result>> {
        return logoPatterns()
                .map {
                    Matchers.regexp(it)
                }
                .toList()
                .toTypedArray()
    }

    private fun logoPatterns(): Sequence<Pattern> {
        return logoLines().map {
            FluentRe
                    .match(Re.beginLine())
                    .then(Re.string(it))
                    .then(Re.repeat(Re.anyCharacter()))
                    .then(Re.endLine())
                    .compile(Pattern.MULTILINE)
        }
    }

    private fun logoLines(): Sequence<String> = buildLogoString().lineSequence()
    private fun buildLogoString(): String {
        return StringBuilder()
                .appendln("""   ______               __""")
                .appendln("""  / ____/     _________/ /___ _""")
                .appendln(""" / /     __  / ___/ __  / __ `/""")
                .appendln("""/ /___  /_/ / /  / /_/ / /_/ /""")
                .appendln("""\____/     /_/   \__,_/\__,_/""")
                .toString()
    }
}

private class ExpectingProcess(private val process: Process, private val expect: Expect) : AutoCloseable {
    override fun close() {
        expect.close()
        process
                .destroyForcibly()
                .waitFor(5, TimeUnit.SECONDS)
    }

    fun exitValue(): Int = process.exitValue()
    fun isAlive(): Boolean = process.isAlive
    fun send(string: String): Expect = expect.send(string)
    fun sendLine(string: String): Expect = expect.sendLine(string)
    fun sendLine(): Expect = expect.sendLine()
    fun sendBytes(bytes: ByteArray): Expect = expect.sendBytes(bytes)
    fun <R : Result> expect(matcher: Matcher<R>): R = expect.expect(matcher)
    fun expect(vararg matchers: Matcher<*>): MultiResult = expect.expect(*matchers)
    fun <R : Result> expectIn(input: Int, matcher: Matcher<R>): R = expect.expectIn(input, matcher)
    fun withTimeout(duration: Long, unit: TimeUnit): Expect = expect.withTimeout(duration, unit)
    fun interact(): InteractBuilder = expect.interact()
    fun interactWith(input: Int): InteractBuilder = expect.interactWith(input)
}

private class ExpectingProcessBuilder(private val command: List<String>, private val workingDirectory: Path) {
    fun start(): ExpectingProcess {
        val process = buildProcess()
        val expect = buildExpect(process)
        return ExpectingProcess(process, expect)
    }

    private fun buildProcess(): Process {
        return ProcessBuilder()
                .command(command)
                .directory(workingDirectoryAsFile())
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

    private fun workingDirectoryAsFile(): File = workingDirectory.toFile()
}

private abstract class JarType(private val nodeDirectory: Path, private val jarName: String) {
    fun directory(): Path = nodeDirectory
    fun directoryName(): String = nodeDirectory.fileName.toString()
    fun jarName(): String = jarName
    fun jarFile(): Path = nodeDirectory.resolve(jarName())
    open fun validateFilesOrThrow() {
        throw NotImplementedError()
    }

    protected fun validateFilesExistOrThrow() {
        nodeDirectory.existsOrThrow()
        jarFile().existsOrThrow()
        configFile().existsOrThrow()
    }

    protected fun configFile(): Path = nodeDirectory.resolve("node.conf")
}

private class CordaJarType(private val nodeDirectory: Path) : JarType(nodeDirectory, "corda.jar") {
    override fun validateFilesOrThrow() = validateFilesExistOrThrow()
}

private class WebserverJarType(private val nodeDirectory: Path) : JarType(nodeDirectory, "corda-webserver.jar") {
    override fun validateFilesOrThrow() {
        validateFilesExistOrThrow()
        validateConfigOrThrow()
    }

    private fun validateConfigOrThrow() {
        val isConfigValid = Files
                .lines(configFile())
                .anyMatch { "webAddress" in it }
        if (!isConfigValid) {
            throw ConfigurationException("Node config does not contain webAddress.")
        }
    }
}

private class JarCommandBuilder(private val jarType: JarType) {
    private var arguments: List<String>? = null
    private var isHeadless: Boolean? = null
    private var isCapsuleDebugOn: Boolean? = null
    fun withArguments(value: List<String>): JarCommandBuilder {
        checkNull(arguments)
        arguments = value
        return this
    }

    fun withHeadlessFlag(): JarCommandBuilder {
        checkNull(isHeadless)
        isHeadless = true
        return this
    }

    fun withCapsuleDebugOn(): JarCommandBuilder {
        checkNull(isCapsuleDebugOn)
        isCapsuleDebugOn = true
        return this
    }

    fun build(): List<String> {
        return ShellCommandBuilder(buildJavaCommand(), directory()).build()
    }

    private fun buildJavaCommand(): List<String> {
        return JavaCommandBuilder(jarFile())
                .withJavaArguments(buildJavaArguments())
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

    private fun directory(): Path = jarType.directory()
    private fun directoryName(): String = jarType.directoryName()
    private fun jarFile(): Path = jarType.jarFile()
    private fun jarName(): String = jarType.jarName()
    private fun arguments(): List<String> = arguments ?: emptyList()
    private fun isHeadless(): Boolean = isHeadless == true
    private fun isCapsuleDebugOn(): Boolean = isCapsuleDebugOn == true
}

private class JavaCommandBuilder(private val jarFile: Path) {
    private var javaArguments: List<String>? = null
    private var jarArguments: List<String>? = null
    fun withJavaArguments(value: List<String>): JavaCommandBuilder {
        checkNull(javaArguments)
        javaArguments = value
        return this
    }

    fun withJarArguments(value: List<String>): JavaCommandBuilder {
        checkNull(jarArguments)
        jarArguments = value
        return this
    }

    fun build(): List<String> {
        return mutableListOf<String>().apply {
            add(javaPathString)
            addAll(javaArguments())
            add("-jar")
            add(jarFileName())
            addAll(jarArguments())
        }
    }

    private val javaPathString: String by lazy {
        Paths
                .get(System.getProperty("java.home"), "bin", "java")
                .toString()
    }

    private fun javaArguments(): List<String> = javaArguments ?: emptyList()
    private fun jarFileName(): String = jarFile.fileName.toString()
    private fun jarArguments(): List<String> = jarArguments ?: emptyList()
}

private class ShellCommandBuilder(private val command: List<String>, private val workingDirectory: Path) {
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
            add("cd $workingDirectory ; ${commandString()} && exit")
        }
    }

    private fun buildLinux(): List<String> {
        return mutableListOf<String>().apply {
            TODO() // TODO Implement this
        }
    }

    private fun commandString(): String = command.joinToString(" ")
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
    private var portNumber: Int = 5005
    fun next(): Int = portNumber++
}

private fun FluentRe.compile(flags: Int): Pattern {
    val patternString = this
            .compile()
            .pattern()
    return Pattern.compile(patternString, flags)
}
