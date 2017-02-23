package net.corda.node

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import net.corda.core.div
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowStateMachine
import net.corda.core.utilities.Emoji
import net.corda.jackson.JacksonSupport
import net.corda.jackson.StringToMethodCallParser
import net.corda.node.internal.Node
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.utilities.ANSIProgressRenderer
import net.corda.nodeapi.ArtemisMessagingComponent
import net.corda.nodeapi.CURRENT_RPC_USER
import net.corda.nodeapi.User
import org.crsh.command.InvocationContext
import org.crsh.console.jline.JLineProcessor
import org.crsh.console.jline.TerminalFactory
import org.crsh.console.jline.console.ConsoleReader
import org.crsh.shell.ShellFactory
import org.crsh.standalone.Bootstrap
import org.crsh.text.Color
import org.crsh.text.RenderPrintWriter
import org.crsh.util.InterruptHandler
import org.crsh.util.Utils
import org.crsh.vfs.FS
import org.crsh.vfs.spi.file.FileMountFactory
import org.crsh.vfs.spi.url.ClassPathMountFactory
import rx.Observable
import rx.Subscriber
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.PrintWriter
import java.lang.reflect.Constructor
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread

// TODO: Add command history.
// TODO: Command completion.
// TODO: Find a way to inject this directly into CRaSH as a command, without needing JIT source compilation.
// TODO: Add serialisers for InputStream so attachments can be uploaded through the shell.
// TODO: Do something sensible with commands that return a future.
// TODO: Configure default renderers, send objects down the pipeline, add commands to do json/xml/yaml outputs.
// TODO: Add a command to view last N lines/tail/control log4j2 loggers.
// TODO: Review or fix the JVM commands which have bitrotted and some are useless.
// TODO: Get rid of the 'java' command, it's kind of worthless.
// TODO: Fix up the 'dashboard' command which has some rendering issues.
// TODO: Resurrect or reimplement the mail plugin.
// TODO: Make it notice new shell commands added after the node started.

object InteractiveShell {
    private lateinit var node: Node

    /**
     * Starts an interactive shell connected to the local terminal. This shell gives administrator access to the node
     * internals.
     */
    fun startShell(dir: Path, runLocalShell: Boolean, runSSHServer: Boolean, node: Node) {
        this.node = node
        var runSSH = runSSHServer

        Logger.getLogger("").level = Level.OFF   // TODO: Is this really needed?

        val classpathDriver = ClassPathMountFactory(Thread.currentThread().contextClassLoader)
        val fileDriver = FileMountFactory(Utils.getCurrentDirectory());

        val extraCommandsPath = (dir / "shell-commands").toAbsolutePath()
        Files.createDirectories(extraCommandsPath)
        val commandsFS = FS.Builder()
                .register("file", fileDriver)
                .mount("file:" + extraCommandsPath)
                .register("classpath", classpathDriver)
                .mount("classpath:/net/corda/node/shell/")
                .mount("classpath:/crash/commands/")
                .build()
        // TODO: Re-point to our own conf path.
        val confFS = FS.Builder()
                .register("classpath", classpathDriver)
                .mount("classpath:/crash")
                .build()

        val bootstrap = Bootstrap(Thread.currentThread().contextClassLoader, confFS, commandsFS)

        val config = Properties()
        if (runSSH) {
            // TODO: Finish and enable SSH access.
            // This means bringing the CRaSH SSH plugin into the Corda tree and applying Marek's patches
            // found in https://github.com/marekdapps/crash/commit/8a37ce1c7ef4d32ca18f6396a1a9d9841f7ff643
            // to that local copy, as CRaSH is no longer well maintained by the upstream and the SSH plugin
            // that it comes with is based on a very old version of Apache SSHD which can't handle connections
            // from newer SSH clients. It also means hooking things up to the authentication system.
            printBasicNodeInfo("SSH server access is not fully implemented, sorry.")
            runSSH = false
        }

        if (runSSH) {
            // Enable SSH access. Note: these have to be strings, even though raw object assignments also work.
            config["crash.ssh.keypath"] = (dir / "sshkey").toString()
            config["crash.ssh.keygen"] = "true"
            // config["crash.ssh.port"] = node.configuration.sshdAddress.port.toString()
            config["crash.auth"] = "simple"
            config["crash.auth.simple.username"] = "admin"
            config["crash.auth.simple.password"] = "admin"
        }

        bootstrap.config = config
        bootstrap.setAttributes(mapOf(
                "node" to node,
                "services" to node.services,
                "ops" to node.rpcOps,
                "mapper" to shellObjectMapper
        ))
        bootstrap.bootstrap()

        // TODO: Automatically set up the JDBC sub-command with a connection to the database.

        if (runSSH) {
            // printBasicNodeInfo("SSH server listening on address", node.configuration.sshdAddress.toString())
        }

        // Possibly bring up a local shell in the launching terminal window, unless it's disabled.
        if (!runLocalShell)
            return
        val shell = bootstrap.context.getPlugin(ShellFactory::class.java).create(null)
        val terminal = TerminalFactory.create()
        val consoleReader = ConsoleReader("Corda", FileInputStream(FileDescriptor.`in`), System.out, terminal)
        val jlineProcessor = JLineProcessor(terminal.isAnsiSupported, shell, consoleReader, System.out)
        InterruptHandler { jlineProcessor.interrupt() }.install()
        thread(name = "Command line shell processor", isDaemon = true) {
            // Give whoever has local shell access administrator access to the node.
            CURRENT_RPC_USER.set(User(ArtemisMessagingComponent.NODE_USER, "", setOf()))
            Emoji.renderIfSupported {
                jlineProcessor.run()
            }
        }
        thread(name = "Command line shell terminator", isDaemon = true) {
            // Wait for the shell to finish.
            jlineProcessor.closed()
            terminal.restore()
            node.stop()
        }
    }

    val shellObjectMapper: ObjectMapper by lazy {
        // Return a standard Corda Jackson object mapper, configured to use YAML by default and with extra
        // serializers.
        //
        // TODO: This should become the default renderer rather than something used specifically by commands.
        JacksonSupport.createInMemoryMapper(node.services.identityService, YAMLFactory())
    }

    private object ObservableSerializer : JsonSerializer<Observable<*>>() {
        override fun serialize(value: Observable<*>, gen: JsonGenerator, serializers: SerializerProvider) {
            gen.writeString("(observable)")
        }
    }

    private fun createOutputMapper(factory: JsonFactory): ObjectMapper {
        return JacksonSupport.createNonRpcMapper(factory).apply({
            // Register serializers for stateful objects from libraries that are special to the RPC system and don't
            // make sense to print out to the screen. For classes we own, annotations can be used instead.
            val rpcModule = SimpleModule("RPC module")
            rpcModule.addSerializer(Observable::class.java, ObservableSerializer)
            registerModule(rpcModule)

            disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            enable(SerializationFeature.INDENT_OUTPUT)
        })
    }

    private val yamlMapper by lazy { createOutputMapper(YAMLFactory()) }
    private val jsonMapper by lazy { createOutputMapper(JsonFactory()) }

    enum class RpcResponsePrintingFormat {
        yaml, json, tostring
    }

    /**
     * Called from the 'flow' shell command. Takes a name fragment and finds a matching flow, or prints out
     * the list of options if the request is ambiguous. Then parses [inputData] as constructor arguments using
     * the [runFlowFromString] method and starts the requested flow using the [ANSIProgressRenderer] to draw
     * the progress tracker. Ctrl-C can be used to cancel.
     */
    @JvmStatic
    fun runFlowByNameFragment(nameFragment: String, inputData: String, output: RenderPrintWriter) {
        val matches = node.flowLogicFactory.flowWhitelist.keys.filter { nameFragment in it }
        if (matches.size > 1) {
            output.println("Ambigous name provided, please be more specific. Your options are:")
            matches.forEachIndexed { i, s -> output.println("${i+1}. $s", Color.yellow) }
            return
        }
        val match = matches.single()
        val clazz = Class.forName(match)
        if (!FlowLogic::class.java.isAssignableFrom(clazz))
            throw IllegalStateException("Found a non-FlowLogic class in the whitelist? $clazz")
        try {
            @Suppress("UNCHECKED_CAST")
            val fsm = runFlowFromString({ node.services.startFlow(it) }, inputData, clazz as Class<FlowLogic<*>>)
            // Show the progress tracker on the console until the flow completes or is interrupted with a
            // Ctrl-C keypress.
            val latch = CountDownLatch(1)
            ANSIProgressRenderer.onDone = { latch.countDown() }
            ANSIProgressRenderer.progressTracker = (fsm as FlowStateMachineImpl).logic.progressTracker
            try {
                // Wait for the flow to end and the progress tracker to notice. By the time the latch is released
                // the tracker is done with the screen.
                latch.await()
            } catch(e: InterruptedException) {
                ANSIProgressRenderer.progressTracker = null
                // TODO: When the flow framework allows us to kill flows mid-flight, do so here.
            } catch(e: ExecutionException) {
                // It has already been logged by the framework code and printed by the ANSI progress renderer.
            }
        } catch(e: NoApplicableConstructor) {
            output.println("No matching constructor found:", Color.red)
            e.errors.forEach { output.println("- $it", Color.red) }
        }
    }

    class NoApplicableConstructor(val errors: List<String>) : Exception() {
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
    fun runFlowFromString(invoke: (FlowLogic<*>) -> FlowStateMachine<*>,
                          inputData: String, clazz: Class<out FlowLogic<*>>,
                          om: ObjectMapper = shellObjectMapper): FlowStateMachine<*> {
        // For each constructor, attempt to parse the input data as a method call. Use the first that succeeds,
        // and keep track of the reasons we failed so we can print them out if no constructors are usable.
        val parser = StringToMethodCallParser(clazz, om)
        val errors = ArrayList<String>()
        for (ctor in clazz.constructors) {
            var paramNamesFromConstructor: List<String>? = null
            fun getPrototype(ctor: Constructor<*>): List<String> {
                val argTypes = ctor.parameterTypes.map { it.simpleName }
                val prototype = paramNamesFromConstructor!!.zip(argTypes).map { pair ->
                    val (name, type) = pair
                    "$name: $type"
                }
                return prototype
            }
            try {
                // Attempt construction with the given arguments.
                paramNamesFromConstructor = parser.paramNamesFromConstructor(ctor)
                val args = parser.parseArguments(clazz.name, paramNamesFromConstructor.zip(ctor.parameterTypes), inputData)
                if (args.size != ctor.parameterTypes.size) {
                    errors.add("${getPrototype(ctor)}: Wrong number of arguments (${args.size} provided, ${ctor.parameterTypes.size} needed)")
                    continue
                }
                val flow = ctor.newInstance(*args) as FlowLogic<*>
                return invoke(flow)
            } catch(e: StringToMethodCallParser.UnparseableCallException.MissingParameter) {
                errors.add("${getPrototype(ctor)}: missing parameter ${e.paramName}")
            } catch(e: StringToMethodCallParser.UnparseableCallException.TooManyParameters) {
                errors.add("${getPrototype(ctor)}: too many parameters")
            } catch(e: StringToMethodCallParser.UnparseableCallException.ReflectionDataMissing) {
                val argTypes = ctor.parameterTypes.map { it.simpleName }
                errors.add("$argTypes: <constructor missing parameter reflection data>")
            } catch(e: StringToMethodCallParser.UnparseableCallException) {
                val argTypes = ctor.parameterTypes.map { it.simpleName }
                errors.add("$argTypes: ${e.message}")
            }
        }
        throw NoApplicableConstructor(errors)
    }

    @JvmStatic
    fun printAndFollowRPCResponse(outputFormat: RpcResponsePrintingFormat, response: Any?, toStream: PrintWriter): CompletableFuture<Unit>? {
        val printerFun = when (outputFormat) {
            RpcResponsePrintingFormat.yaml -> { obj: Any? -> yamlMapper.writeValueAsString(obj) }
            RpcResponsePrintingFormat.json -> { obj: Any? -> jsonMapper.writeValueAsString(obj) }
            RpcResponsePrintingFormat.tostring -> { obj: Any? -> Emoji.renderIfSupported { obj.toString() } }
        }
        toStream.println(printerFun(response))
        toStream.flush()
        return maybeFollow(response, printerFun, toStream)
    }

    private class PrintingSubscriber(private val printerFun: (Any?) -> String, private val toStream: PrintWriter) : Subscriber<Any>() {
        private var count = 0;
        val future = CompletableFuture<Unit>()

        init {
            // The future is public and can be completed by something else to indicate we don't wish to follow
            // anymore (e.g. the user pressing Ctrl-C).
            future.thenAccept {
                if (!isUnsubscribed)
                    unsubscribe()
            }
        }

        @Synchronized
        override fun onCompleted() {
            toStream.println("Observable has completed")
            future.complete(Unit)
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
            e.printStackTrace()
            future.completeExceptionally(e)
        }
    }

    // Kotlin bug: USELESS_CAST warning is generated below but the IDE won't let us remove it.
    @Suppress("USELESS_CAST", "UNCHECKED_CAST")
    private fun maybeFollow(response: Any?, printerFun: (Any?) -> String, toStream: PrintWriter): CompletableFuture<Unit>? {
        // Match on a couple of common patterns for "important" observables. It's tough to do this in a generic
        // way because observables can be embedded anywhere in the object graph, and can emit other arbitrary
        // object graphs that contain yet more observables. So we just look for top level responses that follow
        // the standard "track" pattern, and print them until the user presses Ctrl-C
        if (response == null) return null

        val observable: Observable<*> = when (response) {
            is Observable<*> -> response
            is Pair<*, *> -> when {
                response.first is Observable<*> -> response.first as Observable<*>
                response.second is Observable<*> -> response.second as Observable<*>
                else -> null
            }
            else -> null
        } ?: return null

        val subscriber = PrintingSubscriber(printerFun, toStream)
        (observable as Observable<Any>).subscribe(subscriber)
        return subscriber.future
    }
}
