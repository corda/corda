package net.corda.bridge

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.nodeapi.internal.DEV_CA_KEY_STORE_PASS
import net.corda.nodeapi.internal.DEV_CA_TRUST_STORE_PASS
import net.corda.testing.core.*
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappsForPackages
import net.corda.testing.node.internal.internalDriver
import org.apache.activemq.artemis.core.server.ActiveMQServer
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class SNIBridgeTest(private val withFloat: Boolean) : IntegrationTest() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "with float = {0}")
        fun data() = listOf(false, true)

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(DUMMY_BANK_A_NAME, DUMMY_BANK_B_NAME, DUMMY_BANK_C_NAME, DUMMY_NOTARY_NAME)
    }

    @Test
    fun `Nodes behind all in one bridge can communicate with external node`() {
        val demoUser = User("demo", "demo", setOf(Permissions.startFlow<Ping>(), Permissions.all()))
        var artemis: ActiveMQServer? = null
        internalDriver(startNodesInProcess = true, cordappsForAllNodes = cordappsForPackages("net.corda.bridge"), notarySpecs = emptyList(), portAllocation = incrementalPortAllocation()) {
            val artemisPort = portAllocation.nextPort()
            val advertisedP2PPort = portAllocation.nextPort()
            val floatPort = if (withFloat) portAllocation.nextPort() else null

            val bankAPath = driverDirectory / DUMMY_BANK_A_NAME.organisation / "node"
            val bankBPath = driverDirectory / DUMMY_BANK_B_NAME.organisation / "node"
            val bankCPath = driverDirectory / DUMMY_BANK_C_NAME.organisation / "node"

            // Create node's certificates without starting up the nodes.
            createNodeDevCertificates(DUMMY_BANK_A_NAME, bankAPath)
            createNodeDevCertificates(DUMMY_BANK_B_NAME, bankBPath)
            createNodeDevCertificates(DUMMY_BANK_C_NAME, bankCPath)

            // Start broker
            artemis = createArtemis(driverDirectory, artemisPort)
            artemis!!.start()
            val artemisCertDir = driverDirectory / "artemis"
            val artemisSSLConfig = mapOf(
                    "sslKeystore" to (artemisCertDir / ARTEMIS_KEYSTORE).toString(),
                    "keyStorePassword" to DEV_CA_KEY_STORE_PASS,
                    "trustStoreFile" to (artemisCertDir / ARTEMIS_TRUSTSTORE).toString(),
                    "trustStorePassword" to DEV_CA_TRUST_STORE_PASS
            )

            val aFuture = startNode(
                    providedName = DUMMY_BANK_A_NAME,
                    rpcUsers = listOf(demoUser),
                    customOverrides = mapOf(
                            "baseDirectory" to "$bankAPath",
                            "p2pAddress" to "localhost:$advertisedP2PPort",
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
                            "p2pAddress" to "localhost:$advertisedP2PPort",
                            "messagingServerAddress" to "0.0.0.0:$artemisPort",
                            "messagingServerExternal" to true,
                            "enterpriseConfiguration" to mapOf(
                                    "externalBridge" to true,
                                    "messagingServerSslConfiguration" to artemisSSLConfig
                            )
                    )
            )

            val b = bFuture.getOrThrow()

            startBridge(driverDirectory, artemisPort, advertisedP2PPort, bankAPath / "certificates" / "sslkeystore.jks", bankBPath / "certificates" / "sslkeystore.jks", floatPort = floatPort).getOrThrow()

            // Start a node on the other side of the bridge
            val c = startNode(providedName = DUMMY_BANK_C_NAME, rpcUsers = listOf(demoUser), customOverrides = mapOf("p2pAddress" to "localhost:${portAllocation.nextPort()}", "baseDirectory" to "$bankCPath")).getOrThrow()

            // BANK_C initiates flows with BANK_A and BANK_B
            CordaRPCClient(c.rpcAddress).use(demoUser.username, demoUser.password) {
                it.proxy.startFlow(::Ping, a.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
                it.proxy.startFlow(::Ping, b.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }

            CordaRPCClient(a.rpcAddress).use(demoUser.username, demoUser.password) {
                it.proxy.startFlow(::Ping, c.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }

            CordaRPCClient(b.rpcAddress).use(demoUser.username, demoUser.password) {
                it.proxy.startFlow(::Ping, c.nodeInfo.singleIdentity(), 5).returnValue.getOrThrow()
            }
        }

        artemis?.stop(false, true)
    }
}