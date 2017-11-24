package net.corda.demobench.model

import javafx.beans.binding.IntegerExpression
import net.corda.core.crypto.SignedData
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.*
import net.corda.core.node.NetworkParameters
import net.corda.core.node.NodeInfo
import net.corda.core.node.NotaryInfo
import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.internal.SerializationEnvironmentImpl
import net.corda.core.serialization.internal._contextSerializationEnv
import net.corda.core.utilities.ByteSequence
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.days
import net.corda.demobench.plugin.CordappController
import net.corda.demobench.pty.R3Pty
import net.corda.nodeapi.internal.NetworkParametersCopier
import net.corda.nodeapi.internal.serialization.AMQP_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.KRYO_P2P_CONTEXT
import net.corda.nodeapi.internal.serialization.SerializationFactoryImpl
import net.corda.nodeapi.internal.serialization.amqp.AMQPServerSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.AbstractKryoSerializationScheme
import net.corda.nodeapi.internal.serialization.kryo.KryoHeaderV0_1
import tornadofx.*
import java.io.IOException
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Level
import kotlin.streams.toList

class NodeController(check: atRuntime = ::checkExists) : Controller() {
    companion object {
        const val firstPort = 10000
        const val minPort = 1024
        const val maxPort = 65535
    }

    private val jvm by inject<JVMConfig>()
    private val cordappController by inject<CordappController>()
    private val nodeInfoFilesCopier by inject<DemoBenchNodeInfoFilesCopier>()

    private var baseDir: Path = baseDirFor(ManagementFactory.getRuntimeMXBean().startTime)
    private val cordaPath: Path = jvm.applicationDir.resolve("corda").resolve("corda.jar")
    private val command = jvm.commandFor(cordaPath).toTypedArray()

    private val nodes = LinkedHashMap<String, NodeConfigWrapper>()
    private var notaryIdentity: Party? = null
    private lateinit var networkParametersCopier: NetworkParametersCopier
    private val port = AtomicInteger(firstPort)

    val activeNodes: List<NodeConfigWrapper>
        get() = nodes.values.filter {
            (it.state == NodeState.RUNNING) || (it.state == NodeState.STARTING)
        }

    init {
        log.info("Base directory: $baseDir")
        log.info("Corda JAR: $cordaPath")

        // Check that the Corda capsule is available.
        // We do NOT want to do this during unit testing!
        check(cordaPath, "Cannot find Corda JAR.")
    }

    /**
     * Validate a Node configuration provided by [net.corda.demobench.views.NodeTabView].
     */
    fun validate(nodeData: NodeData): NodeConfigWrapper? {
        fun IntegerExpression.toLocalAddress() = NetworkHostAndPort("localhost", value)

        val location = nodeData.nearestCity.value
        val notary = nodeData.extraServices.filterIsInstance<NotaryService>().noneOrSingle()
        val nodeConfig = NodeConfig(
                myLegalName = CordaX500Name(
                        organisation = nodeData.legalName.value.trim(),
                        locality = location.description,
                        country = location.countryCode
                ),
                p2pAddress = nodeData.p2pPort.toLocalAddress(),
                rpcAddress = nodeData.rpcPort.toLocalAddress(),
                webAddress = nodeData.webPort.toLocalAddress(),
                notary = notary,
                h2port = nodeData.h2Port.value,
                issuableCurrencies = nodeData.extraServices.filterIsInstance<CurrencyIssuer>().map { it.currency.toString() }
        )

        if (notary != null && notaryIdentity != null && notaryIdentity?.name != nodeConfig.myLegalName)
            throw IllegalArgumentException("Only single notary allowed")

        val wrapper = NodeConfigWrapper(baseDir, nodeConfig)

        if (nodes.putIfAbsent(wrapper.key, wrapper) != null) {
            log.warning("Node with key '${wrapper.key}' already exists.")
            return null
        }

        nodeInfoFilesCopier.addConfig(wrapper)

        return wrapper
    }

    fun dispose(config: NodeConfigWrapper) {
        config.state = NodeState.DEAD

        nodeInfoFilesCopier.removeConfig(config)
    }

    val nextPort: Int get() = port.andIncrement

    fun isPortValid(port: Int) = (port >= minPort) && (port <= maxPort)

    fun keyExists(key: String) = nodes.keys.contains(key)

    fun nameExists(name: String) = keyExists(name.toKey())

    fun hasNotary(): Boolean = activeNodes.any { it.nodeConfig.notary != null }

    fun runCorda(pty: R3Pty, config: NodeConfigWrapper): Boolean {
        try {
            check(notaryIdentity != null || config.nodeConfig.notary != null) { "Can't start node without notary in the network" }
            config.nodeDir.createDirectories()

            // Install any built-in plugins into the working directory.
            cordappController.populate(config)

            // Write this node's configuration file into its working directory.
            val confFile = config.nodeDir / "node.conf"
            Files.write(confFile, config.nodeConfig.toText().toByteArray())

            // Execute the Corda node
            val cordaEnv = System.getenv().toMutableMap().apply {
                jvm.setCapsuleCacheDir(this)
            }
            if (config.nodeConfig.notary != null)
                makeNetworkParametersCopier(config)
            pty.run(command, cordaEnv, config.nodeDir.toString())
            log.info("Launched node: ${config.nodeConfig.myLegalName}")
            networkParametersCopier.install(config.nodeDir)
            return true
        } catch (e: Exception) {
            log.log(Level.SEVERE, "Failed to launch Corda: ${e.message}", e)
            return false
        }
    }

    // Slower version of generating notary identity for Demobench. It uses --just-generate-node-info flag only for one node
    // which is notary. TODO After Shams changes use [ServiceIdentityGenerator] which is much faster and cleaner.
    private fun makeNetworkParametersCopier(config: NodeConfigWrapper) {
        if (notaryIdentity == null) {
            val generateNodeInfoCommand = jvm.commandFor(cordaPath, "--just-generate-node-info").toTypedArray()
            val paramsLog = baseDir.resolve("generate-params.log")
            val process = ProcessBuilder(*generateNodeInfoCommand)
                    .directory(config.nodeDir.toFile())
                    .redirectErrorStream(true)
                    .redirectOutput(paramsLog.toFile())
                    .apply { jvm.setCapsuleCacheDir(environment()) }
                    .start()
            process.waitFor()
            if (process.exitValue() != 0) {
                throw IllegalArgumentException("Notary identity generation process exited with: ${process.exitValue()}")
            }
            try {
                initialiseSerialization()
                notaryIdentity = getNotaryIdentity(config)
                networkParametersCopier = NetworkParametersCopier(NetworkParameters(
                        minimumPlatformVersion = 1,
                        notaries = listOf(NotaryInfo(notaryIdentity!!, config.nodeConfig.notary!!.validating)),
                        modifiedTime = Instant.now(),
                        eventHorizon = 10000.days,
                        maxMessageSize = 40000,
                        maxTransactionSize = 40000,
                        epoch = 1
                ))
            } finally {
                _contextSerializationEnv.set(null)
            }
        }
    }

    private fun initialiseSerialization() {
        val context = if (java.lang.Boolean.getBoolean("net.corda.testing.amqp.enable")) AMQP_P2P_CONTEXT else KRYO_P2P_CONTEXT
        _contextSerializationEnv.set(SerializationEnvironmentImpl(
                SerializationFactoryImpl().apply {
                    registerScheme(KryoDemoSerializationScheme)
                    registerScheme(AMQPServerSerializationScheme())
                },
                context))
    }

    private object KryoDemoSerializationScheme : AbstractKryoSerializationScheme() {
        override fun canDeserializeVersion(byteSequence: ByteSequence, target: SerializationContext.UseCase): Boolean {
            return byteSequence == KryoHeaderV0_1 && target == SerializationContext.UseCase.P2P
        }

        override fun rpcClientKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
        override fun rpcServerKryoPool(context: SerializationContext) = throw UnsupportedOperationException()
    }

    // Get the notary identity from node's NodeInfo file, don't check signature, it's only for demo purposes.
    private fun getNotaryIdentity(config: NodeConfigWrapper): Party {
        val file = config.nodeDir.list { it.filter { "nodeInfo-" in it.toString() }.toList()[0] } // Take only the first NodeInfo file.
        log.info("Reading NodeInfo from file: $file")
        val info = file.readAll().deserialize<SignedData<NodeInfo>>().raw.deserialize()
        return if (info.legalIdentities.size == 2) info.legalIdentities[1] else info.legalIdentities[0]
    }

    fun reset() {
        baseDir = baseDirFor(System.currentTimeMillis())
        log.info("Changed base directory: $baseDir")

        // Wipe out any knowledge of previous nodes.
        nodes.clear()
        nodeInfoFilesCopier.reset()
    }

    /**
     * Add a [NodeConfig] object that has been loaded from a profile.
     */
    fun register(config: NodeConfigWrapper): Boolean {
        if (nodes.putIfAbsent(config.key, config) != null) {
            return false
        }
        nodeInfoFilesCopier.addConfig(config)

        updatePort(config.nodeConfig)

        return true
    }

    /**
     * Creates a node directory that can host a running instance of Corda.
     */
    @Throws(IOException::class)
    fun install(config: InstallConfig): NodeConfigWrapper {
        val installed = config.installTo(baseDir)

        cordappController.useCordappsFor(config).forEach {
            installed.cordappsDir.createDirectories()
            val plugin = it.copyToDirectory(installed.cordappsDir)
            log.info("Installed: $plugin")
        }

        if (!config.deleteBaseDir()) {
            log.warning("Failed to remove '${config.baseDir}'")
        }

        return installed
    }

    private fun updatePort(config: NodeConfig) {
        val nextPort = 1 + arrayOf(config.p2pAddress.port, config.rpcAddress.port, config.webAddress.port, config.h2port).max() as Int
        port.getAndUpdate { Math.max(nextPort, it) }
    }

    private fun baseDirFor(time: Long): Path = jvm.dataHome.resolve(localFor(time))
    private fun localFor(time: Long) = SimpleDateFormat("yyyyMMddHHmmss").format(Date(time))

}
