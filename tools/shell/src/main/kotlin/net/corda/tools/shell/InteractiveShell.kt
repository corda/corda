package net.corda.tools.shell

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.StringToMethodCallParser
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.PermissionException
import net.corda.client.rpc.internal.ReconnectingCordaRPCOps
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.StateMachineRunId
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.internal.messaging.InternalCordaRPCOps
import net.corda.core.messaging.*
import net.corda.tools.shell.utlities.ANSIProgressRenderer
import net.corda.tools.shell.utlities.StdoutANSIProgressRenderer
import org.crsh.command.InvocationContext
import org.crsh.console.jline.JLineProcessor
import org.crsh.console.jline.TerminalFactory
import org.crsh.console.jline.console.ConsoleReader
import org.crsh.lang.impl.java.JavaLanguage
import org.crsh.plugin.CRaSHPlugin
import org.crsh.plugin.PluginContext
import org.crsh.plugin.PluginLifeCycle
import org.crsh.plugin.ServiceLoaderDiscovery
import org.crsh.shell.Shell
import org.crsh.shell.ShellFactory
import org.crsh.shell.impl.command.ExternalResolver
import org.crsh.text.Color
import org.crsh.text.RenderPrintWriter
import org.crsh.util.InterruptHandler
import org.crsh.util.Utils
import org.crsh.vfs.FS
import org.crsh.vfs.spi.file.FileMountFactory
import org.crsh.vfs.spi.url.ClassPathMountFactory
import org.slf4j.LoggerFactory
import rx.Observable
import rx.Subscriber
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.InputStream
import java.io.PrintWriter
import java.lang.reflect.*
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.concurrent.thread

// TODO: Add command history.
// TODO: Command completion.
// TODO: Do something sensible with commands that return a future.
// TODO: Configure default renderers, send objects down the pipeline, add support for xml output format.
// TODO: Add a command to view last N lines/tail/control log4j2 loggers.
// TODO: Review or fix the JVM commands which have bitrotted and some are useless.
// TODO: Get rid of the 'java' command, it's kind of worthless.
// TODO: Fix up the 'dashboard' command which has some rendering issues.
// TODO: Resurrect or reimplement the mail plugin.
// TODO: Make it notice new shell commands added after the node started.

object InteractiveShell {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var rpcOps: (username: String, password: String) -> InternalCordaRPCOps
    private lateinit var ops: InternalCordaRPCOps
    private lateinit var rpcConn: AutoCloseable
    private var shell: Shell? = null
    private var classLoader: ClassLoader? = null
    private lateinit var shellConfiguration: ShellConfiguration
    private var onExit: () -> Unit = {}

    @JvmStatic
    fun getCordappsClassloader() = classLoader

    enum class OutputFormat {
        JSON,
        YAML
    }

    fun startShell(configuration: ShellConfiguration, classLoader: ClassLoader? = null, standalone: Boolean = false) {
        rpcOps = { username: String, password: String ->
            if (standalone) {
                ReconnectingCordaRPCOps(configuration.hostAndPort, username, password, configuration.ssl, classLoader).also {
                    rpcConn = it
                }
            } else {
                val client = CordaRPCClient(hostAndPort = configuration.hostAndPort,
                        configuration = CordaRPCClientConfiguration.DEFAULT.copy(
                                maxReconnectAttempts = 1
                        ),
                        sslConfiguration = configuration.ssl,
                        classLoader = classLoader)
                val connection = client.start(username, password)
                rpcConn = connection
                connection.proxy as InternalCordaRPCOps
            }
        }
        _startShell(configuration, classLoader)
    }

    private fun _startShell(configuration: ShellConfiguration, classLoader: ClassLoader? = null) {
        shellConfiguration = configuration
        InteractiveShell.classLoader = classLoader
        val runSshDaemon = configuration.sshdPort != null

        val config = Properties()
        if (runSshDaemon) {
            // Enable SSH access. Note: these have to be strings, even though raw object assignments also work.
            config["crash.ssh.port"] = configuration.sshdPort?.toString()
            config["crash.auth"] = "corda"
            configuration.sshHostKeyDirectory?.apply {
                val sshKeysDir = configuration.sshHostKeyDirectory.createDirectories()
                config["crash.ssh.keypath"] = (sshKeysDir / "hostkey.pem").toString()
                config["crash.ssh.keygen"] = "true"
            }
        }

        ExternalResolver.INSTANCE.addCommand("output-format", "Commands to inspect and update the output format.", OutputFormatCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("run", "Runs a method from the CordaRPCOps interface on the node.", RunShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("flow", "Commands to work with flows. Flows are how you can change the ledger.", FlowShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("start", "An alias for 'flow start'", StartShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("hashLookup", "Checks if a transaction with matching Id hash exists.", HashLookupShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("attachments", "Commands to extract information about attachments stored within the node", AttachmentShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("checkpoints", "Commands to extract information about checkpoints stored within the node", CheckpointShellCommand::class.java)
        shell = ShellLifecycle(configuration.commandsDirectory).start(config, configuration.user, configuration.password)
    }

    fun runLocalShell(onExit: () -> Unit = {}) {
        this.onExit = onExit
        val terminal = TerminalFactory.create()
        val consoleReader = ConsoleReader("Corda", FileInputStream(FileDescriptor.`in`), System.out, terminal)
        val jlineProcessor = JLineProcessor(terminal.isAnsiSupported, shell, consoleReader, System.out)
        InterruptHandler { jlineProcessor.interrupt() }.install()
        thread(name = "Command line shell processor", isDaemon = true) {
            Emoji.renderIfSupported {
                try {
                    jlineProcessor.run()
                } catch (e: IndexOutOfBoundsException) {
                    log.warn("Cannot parse malformed command.")
                }
            }
        }
        thread(name = "Command line shell terminator", isDaemon = true) {
            // Wait for the shell to finish.
            jlineProcessor.closed()
            log.info("Command shell has exited")
            terminal.restore()
            onExit.invoke()
        }
    }

    class ShellLifecycle(private val shellCommands: Path) : PluginLifeCycle() {
        fun start(config: Properties, localUserName: String = "", localUserPassword: String = ""): Shell {
            val classLoader = this.javaClass.classLoader
            val classpathDriver = ClassPathMountFactory(classLoader)
            val fileDriver = FileMountFactory(Utils.getCurrentDirectory())

            val extraCommandsPath = shellCommands.toAbsolutePath().createDirectories()
            val commandsFS = FS.Builder()
                    .register("file", fileDriver)
                    .mount("file:$extraCommandsPath")
                    .register("classpath", classpathDriver)
                    .mount("classpath:/net/corda/tools/shell/")
                    .mount("classpath:/crash/commands/")
                    .build()
            val confFS = FS.Builder()
                    .register("classpath", classpathDriver)
                    .mount("classpath:/crash")
                    .build()

            val discovery = object : ServiceLoaderDiscovery(classLoader) {
                override fun getPlugins(): Iterable<CRaSHPlugin<*>> {
                    // Don't use the Java language plugin (we may not have tools.jar available at runtime), this
                    // will cause any commands using JIT Java compilation to be suppressed. In CRaSH upstream that
                    // is only the 'jmx' command.
                    return super.getPlugins().filterNot { it is JavaLanguage } + CordaAuthenticationPlugin(rpcOps)
                }
            }
            val attributes = emptyMap<String, Any>()
            val context = PluginContext(discovery, attributes, commandsFS, confFS, classLoader)
            context.refresh()
            this.config = config
            start(context)
            ops = makeRPCOps(rpcOps, localUserName, localUserPassword)
            return context.getPlugin(ShellFactory::class.java).create(null, CordaSSHAuthInfo(false, ops, StdoutANSIProgressRenderer))
        }
    }

    fun nodeInfo() = try {
        ops.nodeInfo()
    } catch (e: UndeclaredThrowableException) {
        throw e.cause ?: e
    }

    @JvmStatic
    fun setOutputFormat(outputFormat: OutputFormat) {
        this.outputFormat = outputFormat
    }

    @JvmStatic
    fun getOutputFormat(): OutputFormat {
        return outputFormat
    }

    fun createYamlInputMapper(rpcOps: CordaRPCOps): ObjectMapper {
        // Return a standard Corda Jackson object mapper, configured to use YAML by default and with extra
        // serializers.
        return JacksonSupport.createDefaultMapper(rpcOps, YAMLFactory(), true).apply {
            val rpcModule = SimpleModule().apply {
                addDeserializer(InputStream::class.java, InputStreamDeserializer)
                addDeserializer(UniqueIdentifier::class.java, UniqueIdentifierDeserializer)
            }
            registerModule(rpcModule)
        }
    }

    private fun createOutputMapper(outputFormat: OutputFormat): ObjectMapper {
        val factory = when(outputFormat) {
            OutputFormat.JSON -> JsonFactory()
            OutputFormat.YAML -> YAMLFactory().disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
        }

        return JacksonSupport.createNonRpcMapper(factory).apply {
            // Register serializers for stateful objects from libraries that are special to the RPC system and don't
            // make sense to print out to the screen. For classes we own, annotations can be used instead.
            val rpcModule = SimpleModule().apply {
                addSerializer(Observable::class.java, ObservableSerializer)
                addSerializer(InputStream::class.java, InputStreamSerializer)
            }
            registerModule(rpcModule)

            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    // TODO: A default renderer could be used, instead of an object mapper. See: http://www.crashub.org/1.3/reference.html#_renderers
    private var outputFormat = OutputFormat.YAML

    @VisibleForTesting
    lateinit var latch: CountDownLatch
        private set

    /**
     * Called from the 'flow' shell command. Takes a name fragment and finds a matching flow, or prints out
     * the list of options if the request is ambiguous. Then parses [inputData] as constructor arguments using
     * the [runFlowFromString] method and starts the requested flow. Ctrl-C can be used to cancel.
     */
    @JvmStatic
    fun runFlowByNameFragment(nameFragment: String,
                              inputData: String,
                              output: RenderPrintWriter,
                              rpcOps: CordaRPCOps,
                              ansiProgressRenderer: ANSIProgressRenderer,
                              inputObjectMapper: ObjectMapper = createYamlInputMapper(rpcOps)) {
        val matches = try {
            rpcOps.registeredFlows().filter { nameFragment in it }
        } catch (e: PermissionException) {
            output.println(e.message ?: "Access denied", Color.red)
            return
        }
        if (matches.isEmpty()) {
            output.println("No matching flow found, run 'flow list' to see your options.", Color.red)
            return
        } else if (matches.size > 1 && matches.find { it.endsWith(nameFragment)} == null) {
            output.println("Ambiguous name provided, please be more specific. Your options are:")
            matches.forEachIndexed { i, s -> output.println("${i + 1}. $s", Color.yellow) }
            return
        }

        val flowName = matches.find { it.endsWith(nameFragment)} ?: matches.single()
        val flowClazz: Class<FlowLogic<*>> = if (classLoader != null) {
            uncheckedCast(Class.forName(flowName, true, classLoader))
        } else {
            uncheckedCast(Class.forName(flowName))
        }
        try {
            // Show the progress tracker on the console until the flow completes or is interrupted with a
            // Ctrl-C keypress.
            val stateObservable = runFlowFromString({ clazz, args -> rpcOps.startTrackedFlowDynamic(clazz, *args) }, inputData, flowClazz, inputObjectMapper)

            latch = CountDownLatch(1)
            ansiProgressRenderer.render(stateObservable, latch::countDown)
            // Wait for the flow to end and the progress tracker to notice. By the time the latch is released
            // the tracker is done with the screen.
            while (!Thread.currentThread().isInterrupted) {
                try {
                    latch.await()
                    break
                } catch (e: InterruptedException) {
                    try {
                        rpcOps.killFlow(stateObservable.id)
                    } finally {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            output.println("Flow completed with result: ${stateObservable.returnValue.get()}")
        } catch (e: NoApplicableConstructor) {
            output.println("No matching constructor found:", Color.red)
            e.errors.forEach { output.println("- $it", Color.red) }
        } catch (e: PermissionException) {
            output.println(e.message ?: "Access denied", Color.red)
        } catch (e: ExecutionException) {
            // ignoring it as already logged by the progress handler subscriber
        } finally {
            InputStreamDeserializer.closeAll()
        }
    }

    class NoApplicableConstructor(val errors: List<String>) : CordaException(this.toString()) {
        override fun toString() = (listOf("No applicable constructor for flow. Problems were:") + errors).joinToString(System.lineSeparator())
    }

    /**
     * Tidies up a possibly generic type name by chopping off the package names of classes in a hard-coded set of
     * hierarchies that are known to be widely used and recognised, and also not have (m)any ambiguous names in them.
     *
     * This is used for printing error messages when something doesn't match.
     */
    private fun maybeAbbreviateGenericType(type: Type, extraRecognisedPackage: String): String {
        val packagesToAbbreviate = listOf("java.", "net.corda.core.", "kotlin.", extraRecognisedPackage)

        fun shouldAbbreviate(typeName: String) = packagesToAbbreviate.any { typeName.startsWith(it) }
        fun abbreviated(typeName: String) = if (shouldAbbreviate(typeName)) typeName.split('.').last() else typeName

        fun innerLoop(type: Type): String = when (type) {
            is ParameterizedType -> {
                val args: List<String> = type.actualTypeArguments.map(::innerLoop)
                abbreviated(type.rawType.typeName) + '<' + args.joinToString(", ") + '>'
            }
            is GenericArrayType -> {
                innerLoop(type.genericComponentType) + "[]"
            }
            is Class<*> -> {
                if (type.isArray)
                    abbreviated(type.simpleName)
                else
                    abbreviated(type.name).replace('$', '.')
            }
            else -> type.toString()
        }

        return innerLoop(type)
    }

    @JvmStatic
    fun killFlowById(id: String,
                     output: RenderPrintWriter,
                     rpcOps: CordaRPCOps,
                     inputObjectMapper: ObjectMapper = createYamlInputMapper(rpcOps)) {
        try {
            val runId = try {
                inputObjectMapper.readValue(id, StateMachineRunId::class.java)
            } catch (e: JsonMappingException) {
                output.println("Cannot parse flow ID of '$id' - expecting a UUID.", Color.red)
                log.error("Failed to parse flow ID", e)
                return
            }

            if (rpcOps.killFlow(runId)) {
                output.println("Killed flow $runId", Color.yellow)
            } else {
                output.println("Failed to kill flow $runId", Color.red)
            }
        } finally {
            output.flush()
        }
    }

    // TODO: This utility is generally useful and might be better moved to the node class, or an RPC, if we can commit to making it stable API.
    /**
     * Given a [FlowLogic] class and a string in one-line Yaml form, finds an applicable constructor and starts
     * the flow, returning the created flow logic. Useful for lightweight invocation where text is preferable
     * to statically typed, compiled code.
     *
     * See the [StringToMethodCallParser] class to learn more about limitations and acceptable syntax.
     *
     * @throws NoApplicableConstructor if no constructor could be found for the given set of types.
     */
    @Throws(NoApplicableConstructor::class)
    fun <T> runFlowFromString(invoke: (Class<out FlowLogic<T>>, Array<out Any?>) -> FlowProgressHandle<T>,
                              inputData: String,
                              clazz: Class<out FlowLogic<T>>,
                              om: ObjectMapper): FlowProgressHandle<T> {

        val errors = ArrayList<String>()
        val parser = StringToMethodCallParser(clazz, om)
        val nameTypeList = getMatchingConstructorParamsAndTypes(parser, inputData, clazz)

        try {
            val args = parser.parseArguments(clazz.name, nameTypeList, inputData)
            return invoke(clazz, args)
        } catch (e: StringToMethodCallParser.UnparseableCallException.ReflectionDataMissing) {
            val argTypes = nameTypeList.map { (_, type) -> type }
            errors.add("$argTypes: <constructor missing parameter reflection data>")
        } catch (e: StringToMethodCallParser.UnparseableCallException) {
            val argTypes = nameTypeList.map { (_, type) -> type }
            errors.add("$argTypes: ${e.message}")
        }
        throw NoApplicableConstructor(errors)
    }

    private fun <T> getMatchingConstructorParamsAndTypes(parser: StringToMethodCallParser<FlowLogic<T>>,
                                                         inputData: String,
                                                         clazz: Class<out FlowLogic<T>>) : List<Pair<String, Type>> {
        val errors = ArrayList<String>()
        val classPackage = clazz.packageName
        lateinit var paramNamesFromConstructor: List<String>

        for (ctor in clazz.constructors) {                // Attempt construction with the given arguments.

            fun getPrototype(): List<String> {
                val argTypes = ctor.genericParameterTypes.map {
                    // If the type name is in the net.corda.core or java namespaces, chop off the package name
                    // because these hierarchies don't have (m)any ambiguous names and the extra detail is just noise.
                    maybeAbbreviateGenericType(it, classPackage)
                }
                return paramNamesFromConstructor.zip(argTypes).map { (name, type) -> "$name: $type" }
            }

            try {
                paramNamesFromConstructor = parser.paramNamesFromConstructor(ctor)
                val nameTypeList = paramNamesFromConstructor.zip(ctor.genericParameterTypes)
                parser.validateIsMatchingCtor(clazz.name, nameTypeList, inputData)
                return nameTypeList

            }
            catch (e: StringToMethodCallParser.UnparseableCallException.MissingParameter) {
                errors.add("${getPrototype()}: missing parameter ${e.paramName}")
            }
            catch (e: StringToMethodCallParser.UnparseableCallException.TooManyParameters) {
                errors.add("${getPrototype()}: too many parameters")
            }
            catch (e: StringToMethodCallParser.UnparseableCallException.ReflectionDataMissing) {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: <constructor missing parameter reflection data>")
            }
            catch (e: StringToMethodCallParser.UnparseableCallException) {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: ${e.message}")
            }
        }
        throw NoApplicableConstructor(errors)
    }

    // TODO Filtering on error/success when we will have some sort of flow auditing, for now it doesn't make much sense.
    @JvmStatic
    fun runStateMachinesView(out: RenderPrintWriter, rpcOps: CordaRPCOps): Any? {
        val proxy = rpcOps
        val (stateMachines, stateMachineUpdates) = proxy.stateMachinesFeed()
        val currentStateMachines = stateMachines.map { StateMachineUpdate.Added(it) }
        val subscriber = FlowWatchPrintingSubscriber(out)
        stateMachineUpdates.startWith(currentStateMachines).subscribe(subscriber)
        var result: Any? = subscriber.future
        if (result is Future<*>) {
            if (!result.isDone) {
                out.cls()
                out.println("Waiting for completion or Ctrl-C ... ")
                out.flush()
            }
            try {
                result = result.get()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: ExecutionException) {
                throw e.rootCause
            } catch (e: InvocationTargetException) {
                throw e.rootCause
            }
        }
        return result
    }

    @JvmStatic
    fun runAttachmentTrustRootsView(
        out: RenderPrintWriter,
        rpcOps: InternalCordaRPCOps
    ): Any {
        return AttachmentTrustTable(out, rpcOps.getAttachmentTrustRoots())
    }

    @JvmStatic
    fun runDumpCheckpoints(rpcOps: InternalCordaRPCOps) {
        rpcOps.dumpCheckpoints()
    }

    @JvmStatic
    fun runRPCFromString(input: List<String>, out: RenderPrintWriter, context: InvocationContext<out Any>, cordaRPCOps: CordaRPCOps,
                         inputObjectMapper: ObjectMapper): Any? {
        val cmd = input.joinToString(" ").trim { it <= ' ' }
        if (cmd.startsWith("startflow", ignoreCase = true)) {
            // The flow command provides better support and startFlow requires special handling anyway due to
            // the generic startFlow RPC interface which offers no type information with which to parse the
            // string form of the command.
            out.println("Please use the 'flow' command to interact with flows rather than the 'run' command.", Color.yellow)
            return null
        } else if (cmd.substringAfter(" ").trim().equals("gracefulShutdown", ignoreCase = true)) {
            return InteractiveShell.gracefulShutdown(out, cordaRPCOps)
        }

        var result: Any? = null
        try {
            InputStreamSerializer.invokeContext = context
            val parser = StringToMethodCallParser(CordaRPCOps::class.java, inputObjectMapper)
            val call = parser.parse(cordaRPCOps, cmd)
            result = call.call()
            if (result != null && result !== kotlin.Unit && result !is Void) {
                result = printAndFollowRPCResponse(result, out, outputFormat)
            }
            if (result is Future<*>) {
                if (!result.isDone) {
                    out.println("Waiting for completion or Ctrl-C ... ")
                    out.flush()
                }
                try {
                    result = result.get()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                } catch (e: ExecutionException) {
                    throw e.rootCause
                } catch (e: InvocationTargetException) {
                    throw e.rootCause
                }
            }
        } catch (e: StringToMethodCallParser.UnparseableCallException) {
            out.println(e.message, Color.red)
            if (e !is StringToMethodCallParser.UnparseableCallException.NoSuchFile) {
                out.println("Please try 'man run' to learn what syntax is acceptable")
            }
        } catch (e: Exception) {
            out.println("RPC failed: ${e.rootCause}", Color.red)
        } finally {
            InputStreamSerializer.invokeContext = null
            InputStreamDeserializer.closeAll()
        }
        return result
    }

    @JvmStatic
    fun gracefulShutdown(userSessionOut: RenderPrintWriter, cordaRPCOps: CordaRPCOps) {

        fun display(statements: RenderPrintWriter.() -> Unit) {
            statements.invoke(userSessionOut)
            userSessionOut.flush()
        }

        var isShuttingDown = false
        try {
            display { println("Orchestrating a clean shutdown, press CTRL+C to cancel...") }
            isShuttingDown = true
            display {
                println("...enabling draining mode")
                println("...waiting for in-flight flows to be completed")
            }
            cordaRPCOps.terminate(true)

            val latch = CountDownLatch(1)
            cordaRPCOps.pendingFlowsCount().updates.doOnError { error ->
                log.error(error.message)
                throw error
            }.doAfterTerminate(latch::countDown).subscribe(
                    // For each update.
                    { (first, second) -> display { println("...remaining: $first / $second") } },
                    // On error.
                    { error ->
                        if (!isShuttingDown) {
                            display { println("RPC failed: ${error.rootCause}", Color.red) }
                        }
                    },
                    // When completed.
                    {
                        rpcConn.close()
                        // This will only show up in the standalone Shell, because the embedded one is killed as part of a node's shutdown.
                        display { println("...done, quitting the shell now.") }
                        onExit.invoke()
                    })
            while (!Thread.currentThread().isInterrupted) {
                try {
                    latch.await()
                    break
                } catch (e: InterruptedException) {
                    try {
                        cordaRPCOps.setFlowsDrainingModeEnabled(false)
                        display { println("...cancelled clean shutdown.") }
                    } finally {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        } catch (e: StringToMethodCallParser.UnparseableCallException) {
            display {
                println(e.message, Color.red)
                println("Please try 'man run' to learn what syntax is acceptable")
            }
        } catch (e: Exception) {
            if (!isShuttingDown) {
                display { println("RPC failed: ${e.rootCause}", Color.red) }
            }
        } finally {
            InputStreamSerializer.invokeContext = null
            InputStreamDeserializer.closeAll()
        }
    }

    private fun printAndFollowRPCResponse(response: Any?, out: PrintWriter, outputFormat: OutputFormat): CordaFuture<Unit> {
        val outputMapper = createOutputMapper(outputFormat)

        val mapElement: (Any?) -> String = { element -> outputMapper.writerWithDefaultPrettyPrinter().writeValueAsString(element) }
        return maybeFollow(response, mapElement, out)
    }

    private class PrintingSubscriber(private val printerFun: (Any?) -> String, private val toStream: PrintWriter) : Subscriber<Any>() {
        private var count = 0
        val future = openFuture<Unit>()

        init {
            // The future is public and can be completed by something else to indicate we don't wish to follow
            // anymore (e.g. the user pressing Ctrl-C).
            future.then { unsubscribe() }
        }

        @Synchronized
        override fun onCompleted() {
            toStream.println("Observable has completed")
            future.set(Unit)
        }

        @Synchronized
        override fun onNext(t: Any?) {
            count++
            toStream.println("Observation $count: " + printerFun(t))
            toStream.flush()
        }

        @Synchronized
        override fun onError(e: Throwable) {
            toStream.println("Observable completed with an error")
            e.printStackTrace(toStream)
            future.setException(e)
        }
    }

    private fun maybeFollow(response: Any?, printerFun: (Any?) -> String, out: PrintWriter): CordaFuture<Unit> {
        // Match on a couple of common patterns for "important" observables. It's tough to do this in a generic
        // way because observables can be embedded anywhere in the object graph, and can emit other arbitrary
        // object graphs that contain yet more observables. So we just look for top level responses that follow
        // the standard "track" pattern, and print them until the user presses Ctrl-C
        if (response == null) return doneFuture(Unit)

        if (response is DataFeed<*, *>) {
            out.println("Snapshot:")
            out.println(printerFun(response.snapshot))
            out.flush()
            out.println("Updates:")
            return printNextElements(response.updates, printerFun, out)
        }
        if (response is Observable<*>) {

            return printNextElements(response, printerFun, out)
        }

        out.println(printerFun(response))
        return doneFuture(Unit)
    }

    private fun printNextElements(elements: Observable<*>, printerFun: (Any?) -> String, out: PrintWriter): CordaFuture<Unit> {

        val subscriber = PrintingSubscriber(printerFun, out)
        uncheckedCast(elements).subscribe(subscriber)
        return subscriber.future
    }

}
