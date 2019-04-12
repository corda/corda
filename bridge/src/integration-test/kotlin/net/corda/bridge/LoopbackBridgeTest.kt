package net.corda.bridge

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.testing.core.DUMMY_BANK_A_NAME
import net.corda.testing.core.DUMMY_BANK_B_NAME
import net.corda.testing.core.DUMMY_BANK_C_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.internalDriver
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.junit.Assume
import org.junit.ClassRule
import org.junit.Test
import kotlin.concurrent.thread

class LoopbackBridgeTest : IntegrationTest() {
    companion object {
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME, DUMMY_BANK_B_NAME, DUMMY_BANK_C_NAME)
    }

    // This test will hang if AMQP bridge is being use instead of Loopback bridge.
    @Test(timeout = 600000)
    fun `Nodes behind one bridge can communicate with each other using loopback bridge - with bridge starts after nodes`() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        var artemis: ActiveMQServer? = null
        internalDriver(startNodesInProcess = true, cordappsForAllNodes = cordappsForPackages("net.corda.bridge"), notarySpecs = emptyList(), portAllocation = incrementalPortAllocation(20000)) {
            val artemisPort = portAllocation.nextPort()
            val advertisedP2PPort = portAllocation.nextPort()

            val bankAPath = driverDirectory / DUMMY_BANK_A_NAME.organisation / "node"
            val bankBPath = driverDirectory / DUMMY_BANK_B_NAME.organisation / "node"

            artemis = createArtemis(driverDirectory, artemisPort)
            artemis!!.start()
            val artemisCertDir = driverDirectory / "artemis"
            val artemisSSLConfig = mapOf(
                    "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
                    "keyStorePassword" to DEV_CA_KEY_STORE_PASS,
                    "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
                    "trustStorePassword" to DEV_CA_TRUST_STORE_PASS
            )

            // The nodes are configured with a wrong P2P address, to ensure AMQP bridge won't work.
            val aFuture = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankAPath",
                            "p2pAddress" to "localhost:0",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            )
                    )
            )

            val a = aFuture.getOrThrow()

            val bFuture = startNode(
                    providedName = DUMMY_BANK_B_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankBPath",
                            "p2pAddress" to "localhost:0",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            )
                    )
            )

            val b = bFuture.getOrThrow()

            val testThread = thread {
                CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                    // Loopback flow test.
                    it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
                }

                CordaRPCClient(b.rpcAddress).use(demoUser.username, demoUser.password) {
                    // Loopback flow test.
                    it.proxy.startFlow(::Ping, a.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
                }
            }

            // Starting the bridge at the end, to test the NodeToBridgeSnapshot message's AMQP bridge convert to Loopback bridge code path.
            startBridge(driverDirectory, artemisPort, advertisedP2PPort, bankAPath / "certificates" / "sslkeystore.jks", bankBPath / "certificates" / "sslkeystore.jks").getOrThrow()

            testThread.join()
        }
        artemis?.stop(false, true)
    }

    // This test will hang if AMQP bridge is being use instead of Loopback bridge.
    @Test(timeout = 600000)
    fun `Nodes behind one bridge can communicate with each other using loopback bridge - with bridge started first`() {
        Assume.assumeTrue(!isRemoteDatabaseMode()) // Enterprise only - disable test when running against remote database, reported in ENT-3470
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        var artemis: ActiveMQServer? = null
        internalDriver(startNodesInProcess = true, cordappsForAllNodes = cordappsForPackages("net.corda.bridge"), notarySpecs = emptyList(), portAllocation = incrementalPortAllocation(20000)) {
            val artemisPort = portAllocation.nextPort()
            val advertisedP2PPort = portAllocation.nextPort()

            val artemisCertDir = driverDirectory / "artemis"
            artemis = createArtemis(driverDirectory, artemisPort)
            artemis!!.start()
            val bankAPath = driverDirectory / DUMMY_BANK_A_NAME.organisation / "node"
            val bankBPath = driverDirectory / DUMMY_BANK_B_NAME.organisation / "node"

            // Create node's certificates without starting up the nodes.
            createNodeDevCertificates(DUMMY_BANK_A_NAME, bankAPath)
            createNodeDevCertificates(DUMMY_BANK_B_NAME, bankBPath)

            startBridge(driverDirectory, artemisPort, advertisedP2PPort, bankAPath / "certificates" / "sslkeystore.jks", bankBPath / "certificates" / "sslkeystore.jks").getOrThrow()

            val artemisSSLConfig = mapOf(
                    "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
                    "keyStorePassword" to DEV_CA_KEY_STORE_PASS,
                    "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
                    "trustStorePassword" to DEV_CA_TRUST_STORE_PASS
            )

            // The nodes are configured with a wrong P2P address, to ensure AMQP bridge won't work.
            val a = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankAPath",
                            "p2pAddress" to "localhost:0",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            )
                    )
            ).getOrThrow()

            val b = startNode(
                    providedName = DUMMY_BANK_B_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankBPath",
                            "p2pAddress" to "localhost:0",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            )
                    )
            ).getOrThrow()

            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                // Loopback flow test.
                it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }

            CordaRPCClient(b.rpcAddress).use(demoUser.username, demoUser.password) {
                // Loopback flow test.
                it.proxy.startFlow(::Ping, a.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }
        }
        artemis?.stop(false, true)
    }
}