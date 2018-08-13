package net.corda.tools.shell

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.StringToMethodCallParser
import net.corda.client.rpc.CordaRPCClientConfiguration
import net.corda.client.rpc.CordaRPCConnection
import net.corda.client.rpc.PermissionException
import net.corda.client.rpc.internal.createCordaRPCClientWithInternalSslAndClassLoader
import net.corda.client.rpc.internal.createCordaRPCClientWithSslAndClassLoader
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.openFuture
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
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.UndeclaredThrowableException
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import kotlin.concurrent.thread

// TODO: Add command history.
// TODO: Command completion.
// TODO: Do something sensible with commands that return a future.
// TODO: Configure default renderers, send objects down the pipeline, add commands to do json/xml/yaml outputs.
// TODO: Add a command to view last N lines/tail/control log4j2 loggers.
// TODO: Review or fix the JVM commands which have bitrotted and some are useless.
// TODO: Get rid of the 'java' command, it's kind of worthless.
// TODO: Fix up the 'dashboard' command which has some rendering issues.
// TODO: Resurrect or reimplement the mail plugin.
// TODO: Make it notice new shell commands added after the node started.

object InteractiveShell {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var rpcOps: (username: String, credentials: String) -> CordaRPCOps
    private lateinit var ops: CordaRPCOps
    private lateinit var connection: CordaRPCConnection
    private var shell: Shell? = null
    private var classLoader: ClassLoader? = null
    private lateinit var shellConfiguration: ShellConfiguration
    private var onExit: () -> Unit = {}
    /**
     * Starts an interactive shell connected to the local terminal. This shell gives administrator access to the node
     * internals.
     */
    fun startShell(configuration: ShellConfiguration, classLoader: ClassLoader? = null) {
        rpcOps = { username: String, credentials: String ->
            val client = createCordaRPCClientWithSslAndClassLoader(hostAndPort = configuration.hostAndPort,
                    configuration = CordaRPCClientConfiguration.DEFAULT.copy(
                            maxReconnectAttempts = 1
                    ),
                    sslConfiguration = configuration.ssl,
                    classLoader = classLoader)
            this.connection = client.start(username, credentials)
            connection.proxy
        }
        _startShell(configuration, classLoader)
    }

    /**
     * Starts an interactive shell connected to the local terminal. This shell gives administrator access to the node
     * internals.
     */
    fun startShellInternal(configuration: ShellConfiguration, classLoader: ClassLoader? = null) {
        rpcOps = { username: String, credentials: String ->
            val client = createCordaRPCClientWithInternalSslAndClassLoader(hostAndPort = configuration.hostAndPort,
                    configuration = CordaRPCClientConfiguration.DEFAULT.copy(
                            maxReconnectAttempts = 1
                    ),
                    sslConfiguration = configuration.nodeSslConfig,
                    classLoader = classLoader)
            this.connection = client.start(username, credentials)
            connection.proxy
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

        ExternalResolver.INSTANCE.addCommand("run", "Runs a method from the CordaRPCOps interface on the node.", RunShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("flow", "Commands to work with flows. Flows are how you can change the ledger.", FlowShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("start", "An alias for 'flow start'", StartShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("hashLookup", "Checks if a transaction with matching Id hash exists.", HashLookupShellCommand::class.java)
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
                jlineProcessor.run()
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

    private fun createOutputMapper(): ObjectMapper {
        return JacksonSupport.createNonRpcMapper().apply {
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

    // TODO: This should become the default renderer rather than something used specifically by commands.
    private val outputMapper by lazy { createOutputMapper() }

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
                              om: ObjectMapper) {
        val matches = try {
            rpcOps.registeredFlows().filter { nameFragment in it }
        } catch (e: PermissionException) {
            output.println(e.message ?: "Access denied", Color.red)
            return
        }
        if (matches.isEmpty()) {
            output.println("No matching flow found, run 'flow list' to see your options.", Color.red)
            return
        } else if (matches.size > 1) {
            output.println("Ambiguous name provided, please be more specific. Your options are:")
            matches.forEachIndexed { i, s -> output.println("${i + 1}. $s", Color.yellow) }
            return
        }

        val flowClazz: Class<FlowLogic<*>> = if (classLoader != null) {
            uncheckedCast(Class.forName(matches.single(), true, classLoader))
        } else {
            uncheckedCast(Class.forName(matches.single()))
        }
        try {
            // Show the progress tracker on the console until the flow completes or is interrupted with a
            // Ctrl-C keypress.
            val stateObservable = runFlowFromString({ clazz, args -> rpcOps.startTrackedFlowDynamic(clazz, *args) }, inputData, flowClazz, om)

            val latch = CountDownLatch(1)
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
        // For each constructor, attempt to parse the input data as a method call. Use the first that succeeds,
        // and keep track of the reasons we failed so we can print them out if no constructors are usable.
        val parser = StringToMethodCallParser(clazz, om)
        val errors = ArrayList<String>()
        for (ctor in clazz.constructors) {
            var paramNamesFromConstructor: List<String>? = null
            fun getPrototype(): List<String> {
                val argTypes = ctor.parameterTypes.map { it.simpleName }
                return paramNamesFromConstructor!!.zip(argTypes).map { (name, type) -> "$name: $type" }
            }

            try {
                // Attempt construction with the given arguments.
                paramNamesFromConstructor = parser.paramNamesFromConstructor(ctor)
                val args = parser.parseArguments(clazz.name, paramNamesFromConstructor.zip(ctor.parameterTypes), inputData)
                if (args.size != ctor.parameterTypes.size) {
                    errors.add("${getPrototype()}: Wrong number of arguments (${args.size} provided, ${ctor.parameterTypes.size} needed)")
                    continue
                }
                val flow = ctor.newInstance(*args) as FlowLogic<*>
                if (flow.progressTracker == null) {
                    errors.add("A flow must override the progress tracker in order to be run from the shell")
                    continue
                }
                return invoke(clazz, args)
            } catch (e: StringToMethodCallParser.UnparseableCallException.MissingParameter) {
                errors.add("${getPrototype()}: missing parameter ${e.paramName}")
            } catch (e: StringToMethodCallParser.UnparseableCallException.TooManyParameters) {
                errors.add("${getPrototype()}: too many parameters")
            } catch (e: StringToMethodCallParser.UnparseableCallException.ReflectionDataMissing) {
                val argTypes = ctor.parameterTypes.map { it.simpleName }
                errors.add("$argTypes: <constructor missing parameter reflection data>")
            } catch (e: StringToMethodCallParser.UnparseableCallException) {
                val argTypes = ctor.parameterTypes.map { it.simpleName }
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
    fun runRPCFromString(input: List<String>, out: RenderPrintWriter, context: InvocationContext<out Any>, cordaRPCOps: CordaRPCOps, om: ObjectMapper, isSsh: Boolean = false): Any? {
        val cmd = input.joinToString(" ").trim { it <= ' ' }
        if (cmd.startsWith("startflow", ignoreCase = true)) {
            // The flow command provides better support and startFlow requires special handling anyway due to
            // the generic startFlow RPC interface which offers no type information with which to parse the
            // string form of the command.
            out.println("Please use the 'flow' command to interact with flows rather than the 'run' command.", Color.yellow)
            return null
        } else if (cmd.substringAfter(" ").trim().equals("gracefulShutdown", ignoreCase = true)) {
            return InteractiveShell.gracefulShutdown(out, cordaRPCOps, isSsh)
        }

        var result: Any? = null
        try {
            InputStreamSerializer.invokeContext = context
            val parser = StringToMethodCallParser(CordaRPCOps::class.java, om)
            val call = parser.parse(cordaRPCOps, cmd)
            result = call.call()
            if (result != null && result !== kotlin.Unit && result !is Void) {
                result = printAndFollowRPCResponse(result, out)
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
            out.println("Please try 'man run' to learn what syntax is acceptable")
        } catch (e: Exception) {
            out.println("RPC failed: ${e.rootCause}", Color.red)
        } finally {
            InputStreamSerializer.invokeContext = null
            InputStreamDeserializer.closeAll()
        }
        return result
    }


    @JvmStatic
    fun gracefulShutdown(userSessionOut: RenderPrintWriter, cordaRPCOps: CordaRPCOps, isSsh: Boolean = false) {

        fun display(statements: RenderPrintWriter.() -> Unit) {
            statements.invoke(userSessionOut)
            userSessionOut.flush()
        }

        var isShuttingDown = false
        try {
            display {
                println("Orchestrating a clean shutdown...")
                println("...enabling draining mode")
            }
            cordaRPCOps.setFlowsDrainingModeEnabled(true)
            display {
                println("...waiting for in-flight flows to be completed")
            }
            cordaRPCOps.pendingFlowsCount().updates
                    .doOnError { error ->
                        log.error(error.message)
                        throw error
                    }
                    .doOnNext { (first, second) ->
                        display {
                            println("...remaining: ${first}/${second}")
                        }
                    }
                    .doOnCompleted {
                        if (isSsh) {
                            // print in the original Shell process
                            System.out.println("Shutting down the node via remote SSH session (it may take a while)")
                        }
                        display {
                            println("Shutting down the node (it may take a while)")
                        }
                        cordaRPCOps.shutdown()
                        isShuttingDown = true
                        connection.forceClose()
                        display {
                            println("...done, quitting standalone shell now.")
                        }
                        onExit.invoke()
                    }.toBlocking().single()
        } catch (e: StringToMethodCallParser.UnparseableCallException) {
            display {
                println(e.message, Color.red)
                println("Please try 'man run' to learn what syntax is acceptable")
            }
        } catch (e: Exception) {
            if (!isShuttingDown) {
                display {
                    println("RPC failed: ${e.rootCause}", Color.red)
                }
            }
        } finally {
            InputStreamSerializer.invokeContext = null
            InputStreamDeserializer.closeAll()
        }
    }

    private fun printAndFollowRPCResponse(response: Any?, out: PrintWriter): CordaFuture<Unit> {

        val mapElement: (Any?) -> String = { element -> outputMapper.writerWithDefaultPrettyPrinter().writeValueAsString(element) }
        val mappingFunction: (Any?) -> String = { value ->
            if (value is Collection<*>) {
                value.joinToString(",${System.lineSeparator()}  ", "[${System.lineSeparator()}  ", "${System.lineSeparator()}]") { element ->
                    mapElement(element)
                }
            } else {
                mapElement(value)
            }
        }
        return maybeFollow(response, mappingFunction, out)
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
