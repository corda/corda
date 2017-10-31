package net.corda.testing.node

import net.corda.core.concurrent.CordaFuture
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.*
import net.corda.core.internal.createDirectories
import net.corda.core.internal.div
import net.corda.core.node.NodeInfo
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.internal.Node
import net.corda.node.internal.StartedNode
import net.corda.node.internal.cordapp.CordappLoader
import net.corda.node.services.config.*
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.nodeapi.User
import net.corda.nodeapi.config.toConfig
import net.corda.testing.driver.addressMustNotBeBoundFuture
import net.corda.testing.getFreeLocalPorts
import net.corda.testing.node.MockServices.Companion.MOCK_VERSION_INFO
import net.corda.testing.SerializationEnvironmentRule
import org.apache.logging.log4j.Level
import org.junit.After
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Extend this class if you need to run nodes in a test. You could use the driver DSL but it's extremely slow for testing
 * purposes. Use the driver if you need to run the nodes in separate processes otherwise this class will suffice.
 */
// TODO Some of the logic here duplicates what's in the driver
abstract class NodeBasedTest(private val cordappPackages: List<String> = emptyList()) {
    companion object {
        private val WHITESPACE = "\\s++".toRegex()
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val nodes = mutableListOf<StartedNode<Node>>()
    private val nodeInfos = mutableListOf<NodeInfo>()

    init {
        System.setProperty("consoleLogLevel", Level.DEBUG.name().toLowerCase())
    }

    /**
     * Stops the network map node and all the nodes started by [startNode]. This is called automatically after each test
     * but can also be called manually within a test.
     */
    @After
    fun stopAllNodes() {
        val shutdownExecutor = Executors.newScheduledThreadPool(nodes.size)
        nodes.map { shutdownExecutor.fork(it::dispose) }.transpose().getOrThrow()
        // Wait until ports are released
        val portNotBoundChecks = nodes.flatMap {
            listOf(
                    it.internals.configuration.p2pAddress.let { addressMustNotBeBoundFuture(shutdownExecutor, it) },
                    it.internals.configuration.rpcAddress?.let { addressMustNotBeBoundFuture(shutdownExecutor, it) }
            )
        }.filterNotNull()
        nodes.clear()
        portNotBoundChecks.transpose().getOrThrow()
    }

    /**
     *  Clear network map data from nodes' databases.
     */
    fun clearAllNodeInfoDb() {
        nodes.forEach { it.services.networkMapCache.clearNetworkMapCache() }
    }

    @JvmOverloads
    fun startNode(legalName: CordaX500Name,
                  platformVersion: Int = 1,
                  rpcUsers: List<User> = emptyList(),
                  configOverrides: Map<String, Any> = emptyMap(),
                  waitForConnection: Boolean = true): CordaFuture<StartedNode<Node>> {
        val node = startNodeInternal(
                legalName,
                platformVersion,
                rpcUsers,
                configOverrides)
        return if (waitForConnection) node.internals.nodeReadyFuture.map { node } else doneFuture(node)
    }

    // TODO This method has been added temporarily, to be deleted once the set of notaries is defined at the network level.
    fun startNotaryNode(name: CordaX500Name,
                        rpcUsers: List<User> = emptyList(),
                        validating: Boolean = true): CordaFuture<StartedNode<Node>> {
        return startNode(name, rpcUsers = rpcUsers, configOverrides = mapOf("notary" to mapOf("validating" to validating)))
    }

    fun startNotaryCluster(notaryName: CordaX500Name, clusterSize: Int): CordaFuture<List<StartedNode<Node>>> {
        fun notaryConfig(nodeAddress: NetworkHostAndPort, clusterAddress: NetworkHostAndPort? = null): Map<String, Any> {
            val clusterAddresses = if (clusterAddress != null) listOf(clusterAddress) else emptyList()
            val config = NotaryConfig(validating = true, raft = RaftConfig(nodeAddress = nodeAddress, clusterAddresses = clusterAddresses))
            return mapOf("notary" to config.toConfig().root().unwrapped())
        }

        ServiceIdentityGenerator.generateToDisk(
                (0 until clusterSize).map { baseDirectory(notaryName.copy(organisation = "${notaryName.organisation}-$it")) },
                notaryName)

        val nodeAddresses = getFreeLocalPorts("localhost", clusterSize)

        val masterNodeFuture = startNode(
                CordaX500Name(organisation = "${notaryName.organisation}-0", locality = notaryName.locality, country = notaryName.country),
                configOverrides = notaryConfig(nodeAddresses[0]) + mapOf(
                        "database" to mapOf(
                                "serverNameTablePrefix" to if (clusterSize > 1) "${notaryName.organisation}0".replace(Regex("[^0-9A-Za-z]+"), "") else ""
                        )
                )
        )

        val remainingNodesFutures = (1 until clusterSize).map {
            startNode(
                    CordaX500Name(organisation = "${notaryName.organisation}-$it", locality = notaryName.locality, country = notaryName.country),
                    configOverrides = notaryConfig(nodeAddresses[it], nodeAddresses[0]) + mapOf(
                            "database" to mapOf(
                                    "serverNameTablePrefix" to "${notaryName.organisation}$it".replace(Regex("[^0-9A-Za-z]+"), "")
                            )
                    )
            )
        }

        return remainingNodesFutures.transpose().flatMap { remainingNodes ->
            masterNodeFuture.map { masterNode -> listOf(masterNode) + remainingNodes }
        }
    }

    protected fun baseDirectory(legalName: CordaX500Name) = tempFolder.root.toPath() / legalName.organisation.replace(WHITESPACE, "")

    private fun ensureAllNetworkMapCachesHaveAllNodeInfos() {
        val runningNodes = nodes.filter { it.internals.started != null }
        val runningNodesInfo = runningNodes.map { it.info }
        for (node in runningNodes)
            for (nodeInfo in runningNodesInfo) {
                node.services.networkMapCache.addNode(nodeInfo)
            }
    }

    private fun startNodeInternal(legalName: CordaX500Name,
                                  platformVersion: Int,
                                  rpcUsers: List<User>,
                                  configOverrides: Map<String, Any>): StartedNode<Node> {
        val baseDirectory = baseDirectory(legalName).createDirectories()
        val localPort = getFreeLocalPorts("localhost", 2)
        val p2pAddress = configOverrides["p2pAddress"] ?: localPort[0].toString()
        val config = ConfigHelper.loadConfig(
                baseDirectory = baseDirectory,
                allowMissingConfig = true,
                configOverrides = configOf(
                        "myLegalName" to legalName.toString(),
                        "p2pAddress" to p2pAddress,
                        "rpcAddress" to localPort[1].toString(),
                        "rpcUsers" to rpcUsers.map { it.toMap() }
                ) + configOverrides
        )

        val parsedConfig = config.parseAsNodeConfiguration()
        val node = Node(
                parsedConfig,
                MOCK_VERSION_INFO.copy(platformVersion = platformVersion),
                initialiseSerialization = false,
                cordappLoader = CordappLoader.createDefaultWithTestPackages(parsedConfig, cordappPackages)).start()
        nodes += node
        ensureAllNetworkMapCachesHaveAllNodeInfos()
        thread(name = legalName.organisation) {
            node.internals.run()
        }

        return node
    }
}
