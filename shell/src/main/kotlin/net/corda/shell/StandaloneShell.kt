package net.corda.shell

import com.fasterxml.jackson.core.JsonFactory
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
import net.corda.core.internal.*
import net.corda.core.internal.concurrent.doneFuture
import net.corda.core.internal.concurrent.openFuture
import net.corda.core.messaging.*
import net.corda.shell.utlities.ANSIProgressRenderer
import net.corda.shell.utlities.StdoutANSIProgressRenderer
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


object StandaloneShell {
    private val log = LoggerFactory.getLogger(javaClass)
    private lateinit var rpcOps: (username: String?, credentials: String?) -> CordaRPCOps
    private lateinit var connection: CordaRPCOps
    private var shell: Shell? = null
    private var classLoader : ClassLoader? = null
    /**
     * Starts an interactive shell connected to the local terminal. This shell gives administrator access to the node
     * internals.
     */
    fun startShell(configuration: ShellConfiguration, cordaRPCOps: (username: String?, credentials: String?) -> CordaRPCOps, classLoader: ClassLoader? = null, localUserName: String? = null, localUserPassword: String? = null) {
        rpcOps = cordaRPCOps
        StandaloneShell.classLoader = classLoader
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

        ExternalResolver.INSTANCE.addCommand("run", "Runs a method from the CordaRPCOps interface on the node.", net.corda.node.shell.standalone.RunShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("flow", "Commands to work with flows. Flows are how you can change the ledger.", net.corda.node.shell.standalone.FlowShellCommand::class.java)
        ExternalResolver.INSTANCE.addCommand("start", "An alias for 'flow start'", net.corda.node.shell.standalone.StartShellCommand::class.java)
        shell = ShellLifecycle(dir).start(config, localUserName, localUserPassword)

        //if (runSshDaemon) { //TODO refactor
        //    Node.printBasicNodeInfo("SSH server listening on port", configuration.sshd!!.port.toString())
        //}
    }

    fun runLocalShell(onExit: () -> Unit = {}) {
        val terminal = TerminalFactory.create()
        val consoleReader = ConsoleReader("Corda", FileInputStream(FileDescriptor.`in`), System.out, terminal)
        val jlineProcessor = JLineProcessor(terminal.isAnsiSupported, shell, consoleReader, System.out)
        InterruptHandler { jlineProcessor.interrupt() }.install()
        thread(name = "Command line shell processor", isDaemon = true) {
            // Give whoever has local shell access administrator access to the node.
            //val context = RpcAuthContext(net.corda.core.context.InvocationContext.shell(), AdminSubject("SHELL_USER"))
            //CURRENT_RPC_CONTEXT.set(context) //TODO
            println(Thread.currentThread().id)
            Emoji.renderIfSupported {
                jlineProcessor.run()
            }
            onExit.invoke()
            println("THREAD 1 DONE")
        }
        thread(name = "Command line shell terminator", isDaemon = true) {
            // Wait for the shell to finish.
            println(Thread.currentThread().id)
            jlineProcessor.closed()
            onExit.invoke()
            log.info("Command shell has exited")
            //terminal.restore()

            println("THREAD 2 DONE")
        }
    }

    fun connect() {
        val x = connection.nodeInfo()
        println(x)
    }


    class ShellLifecycle(val dir: Path) : PluginLifeCycle() {
        fun start(config: Properties, localUserName: String? = null, localUserPassword: String? = null): Shell {
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
                    return super.getPlugins().filterNot { it is JavaLanguage } + CordaRemoteAuthenticationPlugin(rpcOps) //TODO szsz
                }
            }
            val attributes = emptyMap<String,Any>()
            val context = PluginContext(discovery, attributes, commandsFS, confFS, classLoader)
            context.refresh()
            this.config = config
            start(context)
            connection = makeRPCOps(rpcOps, localUserName, localUserPassword)
            return context.getPlugin(ShellFactory::class.java).create(null, CordaSSHAuthInfo(false, connection, StdoutANSIProgressRenderer))
        }
    }

    fun createOutputMapper(rpcOps: CordaRPCOps): ObjectMapper {
        // Return a standard Corda Jackson object mapper, configured to use YAML by default and with extra
        // serializers.
        return JacksonSupport.createDefaultMapper(rpcOps, YAMLFactory(), true).apply {
            val rpcModule = SimpleModule()
            rpcModule.addDeserializer(InputStream::class.java, InputStreamDeserializer)
            rpcModule.addDeserializer(UniqueIdentifier::class.java, UniqueIdentifierDeserializer)
            rpcModule.addDeserializer(UUID::class.java, UUIDDeserializer)
            registerModule(rpcModule)
        }
    }

    private fun createOutputMapper(factory: JsonFactory): ObjectMapper {
        return JacksonSupport.createNonRpcMapper(factory).apply {
            // Register serializers for stateful objects from libraries that are special to the RPC system and don't
            // make sense to print out to the screen. For classes we own, annotations can be used instead.
            val rpcModule = SimpleModule()
            rpcModule.addSerializer(Observable::class.java, ObservableSerializer)
            rpcModule.addSerializer(InputStream::class.java, InputStreamSerializer)
            registerModule(rpcModule)

            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    // TODO: This should become the default renderer rather than something used specifically by commands.
    private val yamlMapper by lazy { createOutputMapper(YAMLFactory()) }

    /**
     * Called from the 'flow' shell command. Takes a name fragment and finds a matching flow, or prints out
     * the list of options if the request is ambiguous. Then parses [inputData] as constructor arguments using
     * the [runFlowFromString] method and starts the requested flow. Ctrl-C can be used to cancel.
     */
    @JvmStatic
    fun runFlowByNameFragment(nameFragment: String, inputData: String, output: RenderPrintWriter, rpcOps: CordaRPCOps, ansiProgressRenderer: ANSIProgressRenderer, om: ObjectMapper) {
        val matches = rpcOps.registeredFlows().filter { nameFragment in it }
        if (matches.isEmpty()) {
            output.println("No matching flow found, run 'flow list' to see your options.", Color.red)
            return
        } else if (matches.size > 1) {
            output.println("Ambiguous name provided, please be more specific. Your options are:")
            matches.forEachIndexed { i, s -> output.println("${i + 1}. $s", Color.yellow) }
            return
        }

        val clazz: Class<FlowLogic<*>> = if (classLoader != null) {
            uncheckedCast(Class.forName(matches.single(), true, classLoader))
        } else {
            uncheckedCast(Class.forName(matches.single()))
        }
        try {
            // Show the progress tracker on the console until the flow completes or is interrupted with a
            // Ctrl-C keypress.
            val stateObservable = runFlowFromString({ clazz, args -> rpcOps.startTrackedFlowDynamic(clazz, *args) }, inputData, clazz, om)

            val latch = CountDownLatch(1)
            ansiProgressRenderer.render(stateObservable, { latch.countDown() })
            try {
                // Wait for the flow to end and the progress tracker to notice. By the time the latch is released
                // the tracker is done with the screen.
                latch.await()
            } catch (e: InterruptedException) {
                // TODO: When the flow framework allows us to kill flows mid-flight, do so here.
            }
        } catch (e: NoApplicableConstructor) {
            output.println("No matching constructor found:", Color.red)
            e.errors.forEach { output.println("- $it", Color.red) }
        } catch (e: PermissionException) {
            output.println(e.message ?: "Access denied", Color.red)
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
                val args = parser.parseArguments(clazz.name, paramNamesFromConstructor!!.zip(ctor.parameterTypes), inputData)

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
    fun runRPCFromString(input: List<String>, out: RenderPrintWriter, context: InvocationContext<out Any>, cordaRPCOps: CordaRPCOps, om: ObjectMapper): Any? {
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
            val parser = StringToMethodCallParser(CordaRPCOps::class.java, om)//context.attributes["mapper"] as ObjectMapper)
            val call = parser.parse(cordaRPCOps, cmd)
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

    private fun printAndFollowRPCResponse(response: Any?, toStream: PrintWriter): CordaFuture<Unit> {
        val printerFun = yamlMapper::writeValueAsString
        toStream.println(printerFun(response))
        toStream.flush()
        return maybeFollow(response, printerFun, toStream)
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
        }

        @Synchronized
        override fun onError(e: Throwable) {
            toStream.println("Observable completed with an error")
            e.printStackTrace(toStream)
            future.setException(e)
        }
    }

    private fun maybeFollow(response: Any?, printerFun: (Any?) -> String, toStream: PrintWriter): CordaFuture<Unit> {
        // Match on a couple of common patterns for "important" observables. It's tough to do this in a generic
        // way because observables can be embedded anywhere in the object graph, and can emit other arbitrary
        // object graphs that contain yet more observables. So we just look for top level responses that follow
        // the standard "track" pattern, and print them until the user presses Ctrl-C
        if (response == null) return doneFuture(Unit)

        val observable: Observable<*> = when (response) {
            is Observable<*> -> response
            is DataFeed<*, *> -> {
                toStream.println("Snapshot")
                toStream.println(response.snapshot)
                response.updates
            }
            else -> return doneFuture(Unit)
        }

        val subscriber = PrintingSubscriber(printerFun, toStream)
        uncheckedCast(observable).subscribe(subscriber)
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

    /**
     * String value deserialized to [UUID].
     * */
    object UUIDDeserializer : JsonDeserializer<UUID>() {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): UUID {
            //Create UUID object from string.
            return UUID.fromString(p.text)
        }
    }

    //endregion
}
