package net.corda.node

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.RPCException
import net.corda.core.internal.div
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.config.*
import net.corda.node.services.messaging.ArtemisMessagingServer
import net.corda.nodeapi.internal.config.ExternalBrokerConnectionConfiguration
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.MAX_MESSAGE_SIZE
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.rigorousMock
import net.corda.testing.internal.stubs.CertificateStoreStubs
import net.corda.testing.node.User
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.concurrent.thread
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.fail

class ExternalBrokertests : IntegrationTest() {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val portAllocator = PortAllocation.Incremental(10000)

    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Test
    fun `node startup sequence waits for broker to be available using default mode`() {
        val aliceUser = User("alice", "alice", permissions = setOf("ALL"))
        val p2pPort = portAllocator.nextPort()
        val rpcPort = portAllocator.nextPort()
        val broker = createArtemis(p2pPort)
        val nodeConfiguration  = mapOf(
                "baseDirectory" to tempFolder.root.toPath().toString() + "/",
                "devMode" to false, "messagingServerExternal" to true,
                "messagingServerAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "enterpriseConfiguration" to mapOf("externalBridge" to true),
                "keyStorePassword" to "cordacadevpass",
                "trustStorePassword" to "trustpass",
                "rpcSettings.address" to NetworkHostAndPort("localhost", rpcPort).toString())
        driver(DriverParameters(startNodesInProcess = false, notarySpecs = emptyList())) {
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

    @Test
    fun `node terminates if connection to broker has been lost and cannot be re-established`() {
        val aliceUser = User("alice", "alice", permissions = setOf("ALL"))
        val p2pPort = portAllocator.nextPort()
        val broker = createArtemis(p2pPort)
        broker.start()
        val nodeConfiguration  = mapOf(
                "baseDirectory" to tempFolder.root.toPath().toString() + "/",
                "devMode" to false, "messagingServerExternal" to true,
                "messagingServerAddress" to NetworkHostAndPort("localhost", p2pPort).toString(),
                "enterpriseConfiguration" to mapOf("externalBrokerConnectionConfiguration" to "FAIL_FAST"),
                "keyStorePassword" to "cordacadevpass",
                "trustStorePassword" to "trustpass")
        driver(DriverParameters(startNodesInProcess = false, notarySpecs = emptyList())) {
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
            val defaultConfig = ExternalBrokerConnectionConfiguration.FAIL_FAST
            var reconnectTimeout = 0.0
            (1..defaultConfig.reconnectAttempts).forEach {
                reconnectTimeout += defaultConfig.retryInterval.toMillis() * defaultConfig.retryIntervalMultiplier.pow(it - 1)
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

    private fun createArtemis(p2pPort: Int): ArtemisMessagingServer {
        val baseDirectory = tempFolder.root.toPath()
        val certificatesDirectory = baseDirectory / "certificates"
        val signingCertificateStore = CertificateStoreStubs.Signing.withCertificatesDirectory(certificatesDirectory)
        val p2pSslOptions = CertificateStoreStubs.P2P.withCertificatesDirectory(certificatesDirectory)
        val artemisConfig = rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(baseDirectory).whenever(it).baseDirectory
            doReturn(certificatesDirectory).whenever(it).certificatesDirectory
            doReturn(signingCertificateStore).whenever(it).signingCertificateStore
            doReturn(p2pSslOptions).whenever(it).p2pSslOptions
            doReturn(ALICE_NAME).whenever(it).myLegalName
            doReturn(NetworkHostAndPort("localhost", p2pPort)).whenever(it).p2pAddress
            doReturn(EnterpriseConfiguration(MutualExclusionConfiguration(false, "", 20000, 40000), externalBridge = false)).whenever(it).enterpriseConfiguration
            doReturn(null).whenever(it).jmxMonitoringHttpPort
        }

        artemisConfig.configureWithDevSSLCertificate()

        return ArtemisMessagingServer(artemisConfig, NetworkHostAndPort("0.0.0.0", p2pPort), MAX_MESSAGE_SIZE)
    }
}
