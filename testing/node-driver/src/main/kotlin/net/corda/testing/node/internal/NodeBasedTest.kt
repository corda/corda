package net.corda.testing.node.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.internal.PLATFORM_VERSION
import net.corda.core.internal.concurrent.fork
import net.corda.core.internal.concurrent.transpose
import net.corda.core.node.NotaryInfo
import net.corda.core.utilities.getOrThrow
import net.corda.coretesting.internal.testThreadFactory
import net.corda.node.VersionInfo
import net.corda.node.internal.FlowManager
import net.corda.node.internal.Node
import net.corda.node.internal.NodeFlowManager
import net.corda.node.internal.NodeWithInfo
import net.corda.node.services.config.ConfigHelper
import net.corda.node.services.config.FlowOverrideConfig
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configOf
import net.corda.node.services.config.parseAsNodeConfiguration
import net.corda.node.services.config.plus
import net.corda.nodeapi.internal.DevIdentityGenerator
import net.corda.nodeapi.internal.config.toConfig
import net.corda.nodeapi.internal.network.NetworkParametersCopier
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.driver.internal.incrementalPortAllocation
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
import kotlin.io.path.createDirectories
import kotlin.io.path.div

// TODO Some of the logic here duplicates what's in the driver - the reason why it's not straightforward to replace it by
// using DriverDSLImpl in `init()` and `stopAllNodes()` is because of the platform version passed to nodes (driver doesn't
// support this, and it's a property of the Corda JAR)
abstract class NodeBasedTest @JvmOverloads constructor(
    private val cordappPackages: Set<TestCordappInternal> = emptySet(),
    private val notaries: List<CordaX500Name> = emptyList()
) {
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
    protected val notaryNodes = mutableListOf<NodeWithInfo>()
    private val nodes = mutableListOf<NodeWithInfo>()
    private val portAllocation = incrementalPortAllocation()

    init {
        System.setProperty("consoleLogLevel", Level.DEBUG.name().lowercase())
    }

    @Before
    open fun setUp() {
        val notaryInfos = notaries.map { NotaryInfo(installNotary(it), true) } // todo only validating ones
        defaultNetworkParameters = NetworkParametersCopier(testNetworkParameters(notaries = notaryInfos))
        notaries.mapTo(notaryNodes) { startNode(it) }
    }

    private fun installNotary(legalName: CordaX500Name): Party {
        val baseDirectory = baseDirectory(legalName).createDirectories()
        return DevIdentityGenerator.installKeyStoreWithNodeIdentity(baseDirectory, legalName)
    }

    /**
     * Stops the network map node and all the nodes started by [startNode]. This is called automatically after each test
     * but can also be called manually within a test.
     */
    @After
    fun stopAllNodes() {
        val nodesToShut = nodes + notaryNodes
        val shutdownExecutor = Executors.newScheduledThreadPool(nodesToShut.size)
        try {
            nodesToShut.map { shutdownExecutor.fork(it::dispose) }.transpose().getOrThrow()
            // Wait until ports are released
            val portNotBoundChecks = nodesToShut.flatMap {
                listOf(
                        addressMustNotBeBoundFuture(shutdownExecutor, it.node.configuration.p2pAddress),
                        addressMustNotBeBoundFuture(shutdownExecutor, it.node.configuration.rpcOptions.address)
                )
            }
            nodes.clear()
            notaryNodes.clear()
            portNotBoundChecks.transpose().getOrThrow()
        } finally {
            shutdownExecutor.shutdown()
        }
    }

    @JvmOverloads
    fun startNode(legalName: CordaX500Name,
                  platformVersion: Int = PLATFORM_VERSION,
                  rpcUsers: List<User> = emptyList(),
                  configOverrides: Map<String, Any> = emptyMap(),
                  flowManager: FlowManager = NodeFlowManager(FlowOverrideConfig())): NodeWithInfo {
        val baseDirectory = baseDirectory(legalName).createDirectories()
        val p2pAddress = configOverrides["p2pAddress"] ?: portAllocation.nextHostAndPort().toString()
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to legalName.toString(),
                        "p2pAddress" to p2pAddress,
                        "devMode" to true,
                        "rpcSettings.address" to portAllocation.nextHostAndPort().toString(),
                        "rpcSettings.adminAddress" to portAllocation.nextHostAndPort().toString(),
                        "rpcUsers" to rpcUsers.map { it.toConfig().root().unwrapped() }
                ) + configOverrides
        )

        val customCordapps = if (cordappPackages.isNotEmpty()) {
            cordappPackages
        } else {
            cordappsForPackages(getCallerPackage(NodeBasedTest::class)?.let { listOf(it) } ?: emptyList())
        }
        TestCordappInternal.installCordapps(baseDirectory, emptySet(), customCordapps)

        val parsedConfig = config.parseAsNodeConfiguration().value()

        defaultNetworkParameters.install(baseDirectory)
        val node = InProcessNode(parsedConfig, MOCK_VERSION_INFO.copy(platformVersion = platformVersion), flowManager = flowManager)
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
                node.services.networkMapCache.addOrUpdateNode(nodeInfo)
            }
    }
}

class InProcessNode(
        configuration: NodeConfiguration,
        versionInfo: VersionInfo,
        flowManager: FlowManager = NodeFlowManager(configuration.flowOverrides),
        allowHibernateToManageAppSchema: Boolean = true
) : Node(configuration, versionInfo, false, flowManager = flowManager, allowHibernateToManageAppSchema = allowHibernateToManageAppSchema) {
    override val runMigrationScripts: Boolean = true

    override val rxIoScheduler get() = CachedThreadScheduler(testThreadFactory()).also { runOnStop += it::shutdown }

    // Switch journal buffering off or else for many nodes it is possible to receive OOM in un-managed heap space
    override val journalBufferTimeout = 0
}
