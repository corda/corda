package net.corda.testing.node.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.getOrThrow
import net.corda.node.VersionInfo
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.config.*
import net.corda.nodeapi.internal.config.toConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.node.User
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.getFreeLocalPorts
import net.corda.testing.internal.testThreadFactory
import org.apache.logging.log4j.Level
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import rx.internal.schedulers.CachedThreadScheduler
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.concurrent.thread

// TODO Some of the logic here duplicates what's in the driver
abstract class NodeBasedTest(private val cordappPackages: List<String> = emptyList()) {
    companion object {
        private val WHITESPACE = "\\s++".toRegex()
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private lateinit var defaultNetworkParameters: NetworkParametersCopier
    private val nodes = mutableListOf<StartedNode<Node>>()
    private val nodeInfos = mutableListOf<NodeInfo>()

    init {
        System.setProperty("consoleLogLevel", Level.DEBUG.name().toLowerCase())
    }

    @Before
    fun init() {
        defaultNetworkParameters = NetworkParametersCopier(testNetworkParameters())
    }

    /**
     * Stops the network map node and all the nodes started by [startNode]. This is called automatically after each test
     * but can also be called manually within a test.
     */
    @After
    fun stopAllNodes() {
        val shutdownExecutor = Executors.newScheduledThreadPool(nodes.size)
        try {
            nodes.map { shutdownExecutor.fork(it::dispose) }.transpose().getOrThrow()
            // Wait until ports are released
            val portNotBoundChecks = nodes.flatMap {
                listOf(
                        it.internals.configuration.p2pAddress.let { addressMustNotBeBoundFuture(shutdownExecutor, it) },
                        it.internals.configuration.rpcOptions.address.let { addressMustNotBeBoundFuture(shutdownExecutor, it) }
                )
            }.filterNotNull()
            nodes.clear()
            portNotBoundChecks.transpose().getOrThrow()
        } finally {
            shutdownExecutor.shutdown()
        }
    }

    @JvmOverloads
    fun startNode(legalName: CordaX500Name,
                  platformVersion: Int = 1,
                  rpcUsers: List<User> = emptyList(),
                  configOverrides: Map<String, Any> = emptyMap()): StartedNode<Node> {
        val baseDirectory = baseDirectory(legalName).createDirectories()
        val localPort = getFreeLocalPorts("localhost", 3)
        val p2pAddress = configOverrides["p2pAddress"] ?: localPort[0].toString()
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to legalName.toString(),
                        "p2pAddress" to p2pAddress,
                        "devMode" to true,
                        "rpcSettings.address" to localPort[1].toString(),
                        "rpcSettings.adminAddress" to localPort[2].toString(),
                        "rpcUsers" to rpcUsers.map { it.toConfig().root().unwrapped() }
                ) + configOverrides
        )

        val parsedConfig = config.parseAsNodeConfiguration().also { nodeConfiguration ->
            val errors = nodeConfiguration.validate()
            if (errors.isNotEmpty()) {
                throw IllegalStateException("Invalid node configuration. Errors where:${System.lineSeparator()}${errors.joinToString(System.lineSeparator())}")
            }
        }
        defaultNetworkParameters.install(baseDirectory)
        val node = InProcessNode(parsedConfig, MOCK_VERSION_INFO.copy(platformVersion = platformVersion), cordappPackages).start()
        nodes += node
        ensureAllNetworkMapCachesHaveAllNodeInfos()
        thread(name = legalName.organisation) {
            node.internals.run()
        }

        return node
    }

    protected fun baseDirectory(legalName: CordaX500Name): Path {
        return tempFolder.root.toPath() / legalName.organisation.replace(WHITESPACE, "")
    }

    private fun ensureAllNetworkMapCachesHaveAllNodeInfos() {
        val runningNodes = nodes.filter { it.internals.started != null }
        val runningNodesInfo = runningNodes.map { it.info }
        for (node in runningNodes)
            for (nodeInfo in runningNodesInfo) {
                node.services.networkMapCache.addNode(nodeInfo)
            }
    }
}

class InProcessNode(
        configuration: NodeConfiguration, versionInfo: VersionInfo, cordappPackages: List<String>) : Node(
        configuration, versionInfo, false, CordappLoader.createDefaultWithTestPackages(configuration, cordappPackages)) {
    override fun getRxIoScheduler() = CachedThreadScheduler(testThreadFactory()).also { runOnStop += it::shutdown }
}
