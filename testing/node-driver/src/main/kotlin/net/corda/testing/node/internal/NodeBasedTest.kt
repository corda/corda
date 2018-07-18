package net.corda.testing.node.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.node.VersionInfo
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.services.config.*
import net.corda.nodeapi.internal.config.toConfig
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.node.User
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.getFreeLocalPorts
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.internal.DriverDSLImpl.Companion.defaultTestCorDappsForAllNodes
import org.apache.logging.log4j.Level
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import rx.internal.schedulers.CachedThreadScheduler
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlin.concurrent.thread

// TODO Some of the logic here duplicates what's in the driver - the reason why it's not straightforward to replace it by using DriverDSLImpl in `init()` and `stopAllNodes()` is because of the platform version passed to nodes (driver doesn't support this, and it's a property of the Corda JAR)
abstract class NodeBasedTest(private val cordappPackages: List<String> = emptyList()) {
    companion object {
        private val WHITESPACE = "\\s++".toRegex()

        private val logger = loggerFor<NodeBasedTest>()
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
                        addressMustNotBeBoundFuture(shutdownExecutor, it.internals.configuration.p2pAddress),
                        addressMustNotBeBoundFuture(shutdownExecutor, it.internals.configuration.rpcOptions.address)
                )
            }
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

        val cordapps =  defaultTestCorDappsForAllNodes(getCallerPackage()?.let { cordappPackages + it }?.toSet() ?: cordappPackages.toSet())

        val existingCorDappDirectoriesOption = if (config.hasPath(NodeConfiguration.cordappDirectoriesKey)) config.getStringList(NodeConfiguration.cordappDirectoriesKey) else emptyList()

        val individualCorDappsDirectory = config.parseAsNodeConfiguration().baseDirectory / "cordapps"
        val cordappDirectories = existingCorDappDirectoriesOption + individualCorDappsDirectory.toString()

        val specificConfig = config.withValue("cordappDirectories", ConfigValueFactory.fromIterable(cordappDirectories))

        val parsedConfig = specificConfig.parseAsNodeConfiguration().also { nodeConfiguration ->
            val errors = nodeConfiguration.validate()
            if (errors.isNotEmpty()) {
                throw IllegalStateException("Invalid node configuration. Errors where:${System.lineSeparator()}${errors.joinToString(System.lineSeparator())}")
            }
        }

        if (!individualCorDappsDirectory.exists()) {
            individualCorDappsDirectory.toFile().mkdirs()
            cordapps.forEach { cordapp -> cordapp.packageAsJarInDirectory(individualCorDappsDirectory) }
        } else {
            logger.info("Node's specific CorDapps directory $individualCorDappsDirectory already exists, skipping CorDapps packaging for node ${parsedConfig.myLegalName}.")
        }

        defaultNetworkParameters.install(baseDirectory)
        val node = InProcessNode(parsedConfig, MOCK_VERSION_INFO.copy(platformVersion = platformVersion)).start()
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

    // TODO this needs to be duplicated, for it's call-site based - NodeBasedTest shouldn't exist anyway
    private fun getCallerPackage(): String? {

        val stackTrace = Throwable().stackTrace
        val index = stackTrace.indexOfLast { it.className == NodeBasedTest::class.java.name }
        if (index == -1) return null
        val callerPackage = Class.forName(stackTrace[index + 1].className).`package` ?: throw IllegalStateException("Function instantiating driver must be defined in a package.")
        return callerPackage.name
    }
}

class InProcessNode(configuration: NodeConfiguration, versionInfo: VersionInfo) : Node(configuration, versionInfo, false) {

    override fun getRxIoScheduler() = CachedThreadScheduler(testThreadFactory()).also { runOnStop += it::shutdown }
}
