package net.corda.testing.node.internal

import com.typesafe.config.ConfigValueFactory
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.loggerFor
import net.corda.node.VersionInfo
import net.corda.node.internal.EnterpriseNode
import net.corda.node.internal.NodeWithInfo
import net.corda.node.services.config.*
import net.corda.nodeapi.internal.config.toConfig
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.testThreadFactory
import net.corda.testing.node.User
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
abstract class NodeBasedTest(private val cordappPackages: List<String> = emptyList()) : IntegrationTest() {
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

    private val nodes = mutableListOf<NodeWithInfo>()
    private val nodeInfos = mutableListOf<NodeInfo>()
    private val portAllocation = PortAllocation.Incremental(10000)

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
                        addressMustNotBeBoundFuture(shutdownExecutor, it.node.configuration.p2pAddress),
                        addressMustNotBeBoundFuture(shutdownExecutor, it.node.configuration.rpcOptions.address)
                )
            }
            nodes.clear()
            portNotBoundChecks.transpose().getOrThrow()
        } finally {
            shutdownExecutor.shutdown()
        }
    }

    @JvmOverloads
    fun initNode(legalName: CordaX500Name,
                  platformVersion: Int = 4,
                  rpcUsers: List<User> = emptyList(),
                  configOverrides: Map<String, Any> = emptyMap()): InProcessNode {
        val baseDirectory = baseDirectory(legalName).createDirectories()
        val p2pAddress = configOverrides["p2pAddress"] ?: portAllocation.nextHostAndPort().toString()
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOf(
                        "database" to mapOf("runMigration" to "true"),
                        "myLegalName" to legalName.toString(),
                        "p2pAddress" to p2pAddress,
                        "devMode" to true,
                        "rpcSettings.address" to portAllocation.nextHostAndPort().toString(),
                        "rpcSettings.adminAddress" to portAllocation.nextHostAndPort().toString(),
                        "rpcUsers" to rpcUsers.map { it.toConfig().root().unwrapped() }
                ) + configOverrides
        )

        val cordapps =  cordappsForPackages(getCallerPackage(NodeBasedTest::class)?.let { cordappPackages + it } ?: cordappPackages)

        val existingCorDappDirectoriesOption = if (config.hasPath(NodeConfiguration.cordappDirectoriesKey)) config.getStringList(NodeConfiguration.cordappDirectoriesKey) else emptyList()

        val cordappDirectories = existingCorDappDirectoriesOption + TestCordappDirectories.cached(cordapps).map { it.toString() }

        val specificConfig = config.withValue(NodeConfiguration.cordappDirectoriesKey, ConfigValueFactory.fromIterable(cordappDirectories))

        val parsedConfig = specificConfig.parseAsNodeConfiguration().also { nodeConfiguration ->
            val errors = nodeConfiguration.validate()
            if (errors.isNotEmpty()) {
                throw IllegalStateException("Invalid node configuration. Errors where:${System.lineSeparator()}${errors.joinToString(System.lineSeparator())}")
            }
        }

        defaultNetworkParameters.install(baseDirectory)
        return InProcessNode(parsedConfig, MOCK_VERSION_INFO.copy(platformVersion = platformVersion))
    }

    @JvmOverloads
    fun startNode(legalName: CordaX500Name,
                  platformVersion: Int = 4,
                  rpcUsers: List<User> = emptyList(),
                  configOverrides: Map<String, Any> = emptyMap()): NodeWithInfo {
        val node = initNode(legalName,platformVersion, rpcUsers,configOverrides)
        val nodeInfo = node.start()
        val nodeWithInfo = NodeWithInfo(node, nodeInfo)
        nodes += nodeWithInfo

        ensureAllNetworkMapCachesHaveAllNodeInfos()
        thread(name = legalName.organisation) {
            node.run()
        }

        return nodeWithInfo
    }

    protected fun baseDirectory(legalName: CordaX500Name): Path {
        return tempFolder.root.toPath() / legalName.organisation.replace(WHITESPACE, "")
    }

    private fun ensureAllNetworkMapCachesHaveAllNodeInfos() {
        val runningNodes = nodes.filter { it.node.started != null }
        val runningNodesInfo = runningNodes.map { it.info }
        for (node in runningNodes)
            for (nodeInfo in runningNodesInfo) {
                node.services.networkMapCache.addNode(nodeInfo)
            }
    }
}

class InProcessNode(configuration: NodeConfiguration, versionInfo: VersionInfo) : EnterpriseNode(configuration, versionInfo, false) {

    override val rxIoScheduler get() = CachedThreadScheduler(testThreadFactory()).also { runOnStop += it::shutdown }
}
