package net.corda.node.shell

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.google.common.io.Closeables
import net.corda.client.jackson.JacksonSupport
import net.corda.client.jackson.StringToMethodCallParser
import net.corda.client.rpc.PermissionException
import net.corda.core.CordaException
import net.corda.core.concurrent.CordaFuture
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.DataFeed
import net.corda.core.messaging.FlowProgressHandle
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.node.services.IdentityService
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.internal.security.AdminSubject
import net.corda.node.internal.security.RPCSecurityManager
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.messaging.CURRENT_RPC_CONTEXT
import net.corda.node.services.messaging.RpcAuthContext
import net.corda.node.utilities.ANSIProgressRenderer
import net.corda.node.utilities.StdoutANSIProgressRenderer
import net.corda.nodeapi.internal.persistence.CordaPersistence
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
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    private lateinit var node: StartedNode<Node>
    @VisibleForTesting
    internal lateinit var database: CordaPersistence
    private lateinit var rpcOps: CordaRPCOps
    private lateinit var securityManager: RPCSecurityManager
    private lateinit var identityService: IdentityService
    private var shell: Shell? = null
    private lateinit var nodeLegalName: CordaX500Name

    /**
     * Starts an interactive shell connected to the local terminal. This shell gives administrator access to the node
     * internals.
     */
    fun startShell(configuration: NodeConfiguration, cordaRPCOps: CordaRPCOps, securityManager: RPCSecurityManager, identityService: IdentityService, database: CordaPersistence) {
        this.rpcOps = cordaRPCOps
        this.securityManager = securityManager
        this.identityService = identityService
        this.nodeLegalName = configuration.myLegalName
        this.database = database
        val dir = configuration.baseDirectory
        val runSshDaemon = configuration.sshd != null

        val config = Properties()
        if (runSshDaemon) {
            val sshKeysDir = dir / "sshkey"
            sshKeysDir.toFile().mkdirs()

            // Enable SSH access. Note: these have to be strings, even though raw object assignments also work.
            config["crash.ssh.keypath"] = (sshKeysDir / "hostkey.pem").toString()
            config["crash.ssh.keygen"] = "true"
            config["crash.ssh.port"] = configuration.sshd?.port.toString()
            config["crash.auth"] = "corda"
        }

        ExternalResolver.INSTANCE.addCommand("run", "Runs a method from the CordaRPCOps interface on the node.", RunShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("flow", "Commands to work with flows. Flows are how you can change the ledger.", FlowShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("start", "An alias for 'flow start'", StartShellCommand::class.java)
        shell = ShellLifecycle(dir).start(config)

        if (runSshDaemon) {
            Node.printBasicNodeInfo("SSH server listening on port", configuration.sshd!!.port.toString())
        }
    }

    fun runLocalShell(node: StartedNode<Node>) {
        val terminal = TerminalFactory.create()
        val consoleReader = ConsoleReader("Corda", FileInputStream(FileDescriptor.`in`), System.out, terminal)
        val jlineProcessor = JLineProcessor(terminal.isAnsiSupported, shell, consoleReader, System.out)
        InterruptHandler { jlineProcessor.interrupt() }.install()
        thread(name = "Command line shell processor", isDaemon = true) {
            // Give whoever has local shell access administrator access to the node.
            val context = RpcAuthContext(net.corda.core.context.InvocationContext.shell(), AdminSubject("SHELL_USER"))
            CURRENT_RPC_CONTEXT.set(context)
            Emoji.renderIfSupported {
                jlineProcessor.run()
            }
        }
        thread(name = "Command line shell terminator", isDaemon = true) {
            // Wait for the shell to finish.
            jlineProcessor.closed()
            log.info("Command shell has exited")
            terminal.restore()
            node.dispose()
        }
    }

    class ShellLifecycle(val dir: Path) : PluginLifeCycle() {
        fun start(config: Properties): Shell {
            val classLoader = this.javaClass.classLoader
            val classpathDriver = ClassPathMountFactory(classLoader)
            val fileDriver = FileMountFactory(Utils.getCurrentDirectory())

            val extraCommandsPath = (dir / "shell-commands").toAbsolutePath().createDirectories()
            val commandsFS = FS.Builder()
                    .register("file", fileDriver)
                    .mount("file:" + extraCommandsPath)
                    .register("classpath", classpathDriver)
                    .mount("classpath:/net/corda/node/shell/")
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
                    return super.getPlugins().filterNot { it is JavaLanguage } + CordaAuthenticationPlugin(rpcOps, securityManager, nodeLegalName)
                }
            }
            val attributes = mapOf(
                    "ops" to rpcOps,
                    "mapper" to yamlInputMapper
            )
            val context = PluginContext(discovery, attributes, commandsFS, confFS, classLoader)
            context.refresh()
            this.config = config
            start(context)
            return context.getPlugin(ShellFactory::class.java).create(null, CordaSSHAuthInfo(false, makeRPCOpsWithContext(rpcOps, net.corda.core.context.InvocationContext.shell(), AdminSubject("SHELL_USER")), StdoutANSIProgressRenderer))
        }
    }

    private val yamlInputMapper: ObjectMapper by lazy {
        // Return a standard Corda Jackson object mapper, configured to use YAML by default and with extra
        // serializers.
        JacksonSupport.createInMemoryMapper(identityService, YAMLFactory(), true).apply {
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
    fun runFlowByNameFragment(nameFragment: String, inputData: String, output: RenderPrintWriter, rpcOps: CordaRPCOps, ansiProgressRenderer: ANSIProgressRenderer) {
        val matches =
            rpcOps.registeredFlows().filter { nameFragment in it }

        if (matches.isEmpty()) {
            output.println("No matching flow found, run 'flow list' to see your options.", Color.red)
            return
        } else if (matches.size > 1) {
            output.println("Ambiguous name provided, please be more specific. Your options are:")
            matches.forEachIndexed { i, s -> output.println("${i + 1}. $s", Color.yellow) }
            return
        }

        val clazz: Class<FlowLogic<*>> = uncheckedCast(Class.forName(matches.single()))
        try {
            // Show the progress tracker on the console until the flow completes or is interrupted with a
            // Ctrl-C keypress.
            val stateObservable = runFlowFromString({ clazz, args -> rpcOps.startTrackedFlowDynamic(clazz, *args) }, inputData, clazz)

            val latch = CountDownLatch(1)
            ansiProgressRenderer.render(stateObservable, { latch.countDown() })
            while (!Thread.currentThread().isInterrupted) {
                try {
                    latch.await()
                    break
                } catch (e: InterruptedException) {
                    try {
                        // TODO: When the flow framework allows us to kill flows mid-flight, do so here.
                    } finally {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
            stateObservable.returnValue.get()?.apply {
                if (this !is Throwable) {
                    output.println("Flow completed with result: $this")
                }
            }
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
                              om: ObjectMapper = yamlInputMapper): FlowProgressHandle<T> {
        // For each constructor, attempt to parse the input data as a method call. Use the first that succeeds,
        // and keep track of the reasons we failed so we can print them out if no constructors are usable.
        val parser = StringToMethodCallParser(clazz, om)
        val errors = ArrayList<String>()
        for (ctor in clazz.constructors) {
            var paramNamesFromConstructor: List<String>? = null
            fun getPrototype(): List<String> {
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                return paramNamesFromConstructor!!.zip(argTypes).map { (name, type) -> "$name: $type" }
            }

            try {
                // Attempt construction with the given arguments.
                val args = database.transaction {
                    paramNamesFromConstructor = parser.paramNamesFromConstructor(ctor)
                    parser.parseArguments(clazz.name, paramNamesFromConstructor!!.zip(ctor.genericParameterTypes), inputData)
                }
                if (args.size != ctor.genericParameterTypes.size) {
                    errors.add("${getPrototype()}: Wrong number of arguments (${args.size} provided, ${ctor.genericParameterTypes.size} needed)")
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
                val argTypes = ctor.genericParameterTypes.map { it.typeName }
                errors.add("$argTypes: <constructor missing parameter reflection data>")
            } catch (e: StringToMethodCallParser.UnparseableCallException) {
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
        database.transaction {
            stateMachineUpdates.startWith(currentStateMachines).subscribe(subscriber)
        }
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
    fun runRPCFromString(input: List<String>, out: RenderPrintWriter, context: InvocationContext<out Any>, cordaRPCOps: CordaRPCOps): Any? {
        val parser = StringToMethodCallParser(CordaRPCOps::class.java, context.attributes["mapper"] as ObjectMapper)

        val cmd = input.joinToString(" ").trim { it <= ' ' }
        if (cmd.toLowerCase().startsWith("startflow")) {
            // The flow command provides better support and startFlow requires special handling anyway due to
            // the generic startFlow RPC interface which offers no type information with which to parse the
            // string form of the command.
            out.println("Please use the 'flow' command to interact with flows rather than the 'run' command.", Color.yellow)
            return null
        }

        var result: Any? = null
        try {
            InputStreamSerializer.invokeContext = context
            val call = database.transaction { parser.parse(cordaRPCOps, cmd) }
            result = call.call()
            if (result != null && result !is kotlin.Unit && result !is Void) {
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

        if (response !is Observable<*> && response !is DataFeed<*, *>) {
            out.println(printerFun(response))
            return doneFuture(Unit)
        }

        if (response is DataFeed<*, *>) {
            out.println("Snapshot:")
            out.println(printerFun(response.snapshot))
            out.flush()
            out.println("Updates:")
            return printNextElements(response.updates, printerFun, out)
        }
        return printNextElements(response as Observable<*>, printerFun, out)
    }

    private fun printNextElements(elements: Observable<*>, printerFun: (Any?) -> String, out: PrintWriter): CordaFuture<Unit> {

        val subscriber = PrintingSubscriber(printerFun, out)
        uncheckedCast(elements).subscribe(subscriber)
        return subscriber.future
    }

    //region Extra serializers
    //
    // These serializers are used to enable the user to specify objects that aren't natural data containers in the shell,
    // and for the shell to print things out that otherwise wouldn't be usefully printable.

    private object ObservableSerializer : JsonSerializer<Observable<*>>() {
        override fun serialize(value: Observable<*>, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString("(observable)")
        }
    }

    // A file name is deserialized to an InputStream if found.
    object InputStreamDeserializer : JsonDeserializer<InputStream>() {
        // Keep track of them so we can close them later.
        private val streams = Collections.synchronizedSet(HashSet<InputStream>())

        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): InputStream {
            val stream = object : BufferedInputStream(Files.newInputStream(Paths.get(p.text))) {
                override fun close() {
                    super.close()
                    streams.remove(this)
                }
            }
            streams += stream
            return stream
        }

        fun closeAll() {
            // Clone the set with toList() here so each closed stream can be removed from the set inside close().
            streams.toList().forEach { Closeables.closeQuietly(it) }
        }
    }

    // An InputStream found in a response triggers a request to the user to provide somewhere to save it.
    private object InputStreamSerializer : JsonSerializer<InputStream>() {
        var invokeContext: InvocationContext<*>? = null

        override fun serialize(value: InputStream, gen: JsonGenerator, serializers: SerializerProvider) {
            try {
                val toPath = invokeContext!!.readLine("Path to save stream to (enter to ignore): ", true)
                if (toPath == null || toPath.isBlank()) {
                    gen.writeString("<not saved>")
                } else {
                    val path = Paths.get(toPath)
                    value.copyTo(path)
                    gen.writeString("<saved to: ${path.toAbsolutePath()}>")
                }
            } finally {
                try {
                    value.close()
                } catch (e: IOException) {
                    // Ignore.
                }
            }
        }
    }

    /**
     * String value deserialized to [UniqueIdentifier].
     * Any string value used as [UniqueIdentifier.externalId].
     * If string contains underscore(i.e. externalId_uuid) then split with it.
     *      Index 0 as [UniqueIdentifier.externalId]
     *      Index 1 as [UniqueIdentifier.id]
     * */
    object UniqueIdentifierDeserializer : JsonDeserializer<UniqueIdentifier>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UniqueIdentifier {
            //Check if externalId and UUID may be separated by underscore.
            if (p.text.contains("_")) {
                val ids = p.text.split("_")
                //Create UUID object from string.
                val uuid: UUID = UUID.fromString(ids[1])
                //Create UniqueIdentifier object using externalId and UUID.
                return UniqueIdentifier(ids[0], uuid)
            }
            //Any other string used as externalId.
            return UniqueIdentifier.fromString(p.text)
        }
    }

    //endregion
}
