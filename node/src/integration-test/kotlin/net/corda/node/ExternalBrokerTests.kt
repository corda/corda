package net.corda.node

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.internal.div
import net.corda.core.internal.sumByLong
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.contracts.asset.Cash
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.config.EnterpriseConfiguration
import net.corda.node.services.config.MutualExclusionConfiguration
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.config.configureWithDevSSLCertificate
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.config.MessagingServerConnectionConfiguration
import net.corda.testing.core.*
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.User
import org.junit.ClassRule
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.fail

class ExternalBrokertests : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME, DUMMY_NOTARY_NAME)
    }

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val portAllocator = incrementalPortAllocation(10000)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `node startup sequence waits for broker to be available using default mode`() {
        val aliceUser = User("alice", "alice", permissions = setOf("ALL"))
        val p2pPort = portAllocator.nextPort()
        val rpcPort = portAllocator.nextPort()
        val broker = createArtemis(p2pPort)
        val nodeBaseDir = tempFolder.root.toPath()
        val nodeConfiguration = mapOf(
                "baseDirectory" to "$nodeBaseDir",
                "devMode" to false, "messagingServerExternal" to true,
                "messagingServerAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "enterpriseConfiguration" to mapOf(
                        "externalBridge" to true,
                        "messagingServerSslConfiguration" to mapOf(
                                "sslKeystore" to "$nodeBaseDir/certificates/sslkeystore.jks",
                                "keyStorePassword" to "cordacadevpass",
                                "trustStoreFile" to "$nodeBaseDir/certificates/truststore.jks",
                                "trustStorePassword" to "trustpass"
                        )),
                "keyStorePassword" to "cordacadevpass",
                "trustStorePassword" to "trustpass",
                "rpcSettings.address" to NetworkHostAndPort("localhost", rpcPort).toString())
        driver(DriverParameters(startNodesInProcess = false, notarySpecs = emptyList(), portAllocation = portAllocator)) {
            val nodeThread = thread {
                startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser), customOverrides = nodeConfiguration).getOrThrow()
            }

            // Connect RPC client to node (will take some time) and check exception
            try {
                CordaRPCClient(NetworkHostAndPort("localhost", rpcPort)).start(aliceUser.username, aliceUser.password)
            } catch (e: RPCException) {
                assertEquals("Cannot connect to server(s). Tried with all available servers.", e.message)
            }

            broker.start()
            nodeThread.join()

            // Try connecting to the node again (should be running) and execute and RPC
            try {
                CordaRPCClient(NetworkHostAndPort("localhost", rpcPort)).start(aliceUser.username, aliceUser.password).use {
                    try {
                        val nodeInfo = it.proxy.nodeInfo()
                        assertEquals(nodeInfo.legalIdentities.first().name, ALICE_NAME)
                    } catch (e: RPCException) {
                        fail("Calling RPC nodeInfo failed. Node is not running.")
                    }
                    it.close()
                }
            } catch (e: RPCException) {
                fail("Could not connect RPC client to the node.")
            }
        }

        broker.stop()
    }

    // TODO: un-ignore when node will be changed to no longer die on artemis connection loss
    @Ignore
    @Test
    fun `node terminates if connection to broker has been lost and cannot be re-established`() {
        val aliceUser = User("alice", "alice", permissions = setOf("ALL"))
        val p2pPort = portAllocator.nextPort()
        val broker = createArtemis(p2pPort)
        broker.start()
        val nodeBaseDir = tempFolder.root.toPath()
        val nodeConfiguration = mapOf(
                "baseDirectory" to "$nodeBaseDir",
                "devMode" to false, "messagingServerExternal" to true,
                "messagingServerAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "enterpriseConfiguration" to mapOf(
                        "messagingServerConnectionConfiguration" to "FAIL_FAST",
                        "messagingServerSslConfiguration" to mapOf(
                                "sslKeystore" to "$nodeBaseDir/certificates/sslkeystore.jks",
                                "keyStorePassword" to "cordacadevpass",
                                "trustStoreFile" to "$nodeBaseDir/certificates/truststore.jks",
                                "trustStorePassword" to "trustpass"
                        )),
                "keyStorePassword" to "cordacadevpass",
                "trustStorePassword" to "trustpass")
        driver(DriverParameters(startNodesInProcess = false, notarySpecs = emptyList(), portAllocation = portAllocator)) {
            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser), customOverrides = nodeConfiguration).getOrThrow()
            // Check node is running by calling and RPC
            CordaRPCClient(aliceNode.rpcAddress).start(aliceUser.username, aliceUser.password).use {
                try {
                    val nodeInfo = it.proxy.nodeInfo()
                    assertEquals(nodeInfo.legalIdentities.first().name, ALICE_NAME)
                } catch (e: RPCException) {
                    fail("Calling RPC nodeInfo failed. Node is not running.")
                }
                it.close()
            }

            broker.stop()
            val defaultConfig = MessagingServerConnectionConfiguration.FAIL_FAST
            var reconnectTimeout = 0.0
            (1..defaultConfig.reconnectAttempts(isHa = false)).forEach {
                reconnectTimeout += defaultConfig.retryInterval().toMillis() * defaultConfig.retryIntervalMultiplier().pow(it - 1)
            }

            // Wait for the configured reconnection time to pass before attempting and RPC connection and check whether the node is stopped or running
            Thread.sleep(reconnectTimeout.toLong())

            try {
                CordaRPCClient(aliceNode.rpcAddress).start(aliceUser.username, aliceUser.password)
            } catch (e: RPCException) {
                assertEquals("Cannot connect to server(s). Tried with all available servers.", e.message)
            }
        }
    }

    @Test
    @Ignore
    // TODO: Investigate why node will hang without the sleep after broker restart.
    fun `node can still send and recieve message after broker restart`() {
        val p2pPort = portAllocator.nextPort()
        val rootDir = tempFolder.root.toPath()
        val nodeBaseDir = rootDir / "alice"

        val nodeConfiguration = mapOf(
                "baseDirectory" to "$nodeBaseDir",
                "p2pAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "devMode" to false,
                "messagingServerExternal" to true,
                "messagingServerAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "enterpriseConfiguration" to mapOf(
                        "messagingServerConnectionConfiguration" to "CONTINUOUS_RETRY",
                        "messagingServerSslConfiguration" to mapOf(
                                "sslKeystore" to "$nodeBaseDir/certificates/sslkeystore.jks",
                                "keyStorePassword" to "cordacadevpass",
                                "trustStoreFile" to "$nodeBaseDir/certificates/truststore.jks",
                                "trustStorePassword" to "trustpass"
                        )))


        driver(DriverParameters(extraCordappPackagesToScan = listOf("net.corda.finance"),
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME, validating = false)),
                portAllocation = portAllocator)) {
            val aliceUser = User("alice", "alice", permissions = setOf("ALL"))

            val broker = createArtemis(p2pPort, baseDir = rootDir / "broker", certificateDir = nodeBaseDir / "certificates")
            println("starting broker")
            broker.start()

            val aliceNode = startNode(providedName = ALICE_NAME, rpcUsers = listOf(aliceUser), customOverrides = nodeConfiguration).getOrThrow()
            val bobNode = startNode(providedName = BOB_NAME, rpcUsers = listOf(aliceUser)).getOrThrow()

            aliceNode.rpc.startFlow(::CashIssueFlow, 100.POUNDS, OpaqueBytes.of(0), defaultNotaryIdentity).returnValue.getOrThrow()
            println("Spend cash")
            aliceNode.rpc.startFlow(::CashPaymentFlow, 5.POUNDS, bobNode.nodeInfo.singleIdentity()).returnValue.getOrThrow()

            assertEquals(9500, aliceNode.rpc.vaultTotal())

            // Stop the first broker and start a new broker in the same directory.
            println("Stopping broker")
            broker.stop()
            println("Starting broker")
            broker.start()
            Thread.sleep(10000)

            println("Spend cash")
            aliceNode.rpc.startFlow(::CashPaymentFlow, 5.POUNDS, bobNode.nodeInfo.singleIdentity()).returnValue.getOrThrow()

            assertEquals(9000, aliceNode.rpc.vaultTotal())

            // Stop the first broker and start a new broker in the same directory.
            println("Stopping broker")
            broker.stop()
            println("Starting broker")
            broker.start()

            Thread.sleep(10000)

            println("Spend cash")
            aliceNode.rpc.startFlow(::CashPaymentFlow, 5.POUNDS, bobNode.nodeInfo.singleIdentity()).returnValue.getOrThrow()

            assertEquals(8500, aliceNode.rpc.vaultTotal())

            broker.stop()
        }
    }

    private fun CordaRPCOps.vaultTotal() = vaultQuery(Cash.State::class.java).states.sumByLong { it.state.data.amount.quantity }

    private fun createArtemis(p2pPort: Int, baseDir: Path = tempFolder.root.toPath(), certificateDir: Path = baseDir / "certificates", createKeyStore: Boolean = true): ArtemisMessagingServer {
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificateDir)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificateDir)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDir).whenever(it).baseDirectory
            doReturn(certificateDir).whenever(it).certificatesDirectory
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslOptions).whenever(it).p2pSslOptions
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(NetworkHostAndPort("localhost", p2pPort)).whenever(it).p2pAddress
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = false)).whenever(it)
                    .enterpriseConfiguration
            doReturn(null).whenever(it).jmxMonitoringHttpPort
        }
        if (createKeyStore) {
            artemisConfig.configureWithDevSSLCertificate()
        }

        return ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", p2pPort), MAX_MESSAGE_SIZE)
    }
}
