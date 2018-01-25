package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.persistence.configureDatabase
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.cordform.CordformNode
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.concurrent.transpose
import net.corda.core.internal.div
import net.corda.core.internal.exists
import net.corda.core.internal.list
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.finance.DOLLARS
import net.corda.finance.flows.CashIssueAndPaymentFlow
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NetworkParameters
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.CompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import java.net.URL
import java.security.cert.X509Certificate
import java.util.*
import kotlin.streams.toList

// This is the same test as the one in net.corda.node.utilities.registration but using the real doorman and with some
// extra checks on the network map.
class NodeRegistrationTest : IntegrationTest() {
    companion object {
        private val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        private val aliceName = CordaX500Name("Alice", "London", "GB")
        private val genevieveName = CordaX500Name("Genevieve", "London", "GB")

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName.organisation, aliceName.organisation, genevieveName.organisation)

        private val timeoutMillis = 5.seconds.toMillis()
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val portAllocation = PortAllocation.Incremental(10000)
    private val serverAddress = portAllocation.nextHostAndPort()
    private val dbId = random63BitValue().toString()

    private lateinit var rootCaCert: X509Certificate
    private lateinit var csrCa: CertificateAndKeyPair
    private lateinit var networkMapCa: CertificateAndKeyPair

    private var server: NetworkManagementServer? = null

    @Before
    fun init() {
        val (rootCa, doormanCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.csrCa = doormanCa
        networkMapCa = createDevNetworkMapCa(rootCa)
    }

    @After
    fun cleanUp() {
        server?.close()
    }

    @Test
    fun `register nodes with doorman and then they transact with each other`() {
        // Start the server without the network parameters since we don't have them yet
        server = startNetworkManagementServer(networkParameters = null)
        val compatibilityZone = CompatibilityZoneParams(
                URL("http://$serverAddress"),
                publishNotaries = { notaryInfos ->
                    // Restart the server once we're able to generate the network parameters
                    server!!.close()
                    server = startNetworkManagementServer(testNetworkParameters(notaryInfos))
                    // Once restarted we delay starting the nodes to make sure the network map server has processed the
                    // network parameters, otherwise the nodes will fail to start as the network parameters won't be
                    // available for them to download.
                    Thread.sleep(2 * timeoutMillis)
                },
                rootCert = rootCaCert
        )

        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                initialiseSerialization = false,
                notarySpecs = listOf(NotarySpec(notaryName)),
                extraCordappPackagesToScan = listOf("net.corda.finance")
        ) {
            val (alice, notary) = listOf(
                    startNode(providedName = aliceName),
                    defaultNotaryNode
            ).transpose().getOrThrow()

            alice.onlySeesFromNetworkMap(alice, notary)
            notary.onlySeesFromNetworkMap(alice, notary)

            val genevieve = startNode(providedName = genevieveName).getOrThrow()

            // Wait for the nodes to poll again
            Thread.sleep(timeoutMillis * 2)

            // Make sure the new node is visible to everyone
            alice.onlySeesFromNetworkMap(alice, genevieve, notary)
            notary.onlySeesFromNetworkMap(alice, genevieve, notary)
            genevieve.onlySeesFromNetworkMap(alice, genevieve, notary)

            // Check the nodes can communicate among themselves (and the notary).
            val anonymous = false
            genevieve.rpc.startFlow(
                    ::CashIssueAndPaymentFlow,
                    1000.DOLLARS,
                    OpaqueBytes.of(12),
                    alice.nodeInfo.singleIdentity(),
                    anonymous,
                    defaultNotaryIdentity
            ).returnValue.getOrThrow()
        }
    }


    private fun startNetworkManagementServer(networkParameters: NetworkParameters?): NetworkManagementServer {
        return NetworkManagementServer().apply {
            start(
                    serverAddress,
                    configureDatabase(makeTestDataSourceProperties(), DatabaseConfig(runMigration = true)),
                    CertPathAndKey(listOf(csrCa.certificate, rootCaCert), csrCa.keyPair.private),
                    DoormanConfig(approveAll = true, jiraConfig = null, approveInterval = timeoutMillis),
                    networkParameters?.let {
                        NetworkMapStartParams(
                                LocalSigner(networkMapCa),
                                networkParameters,
                                NetworkMapConfig(cacheTimeout = timeoutMillis, signInterval = timeoutMillis)
                        )
                    }
            )
        }
    }

    private fun NodeHandle.onlySeesFromNetworkMap(vararg nodes: NodeHandle) {
        // Make sure the nodes aren't getting the node infos from their additional directories
        val nodeInfosDir = configuration.baseDirectory / CordformNode.NODE_INFO_DIRECTORY
        if (nodeInfosDir.exists()) {
            assertThat(nodeInfosDir.list { it.toList() }).isEmpty()
        }
        assertThat(rpc.networkMapSnapshot()).containsOnlyElementsOf(nodes.map { it.nodeInfo })
    }

    // TODO Use the other dbs in the integration tests
    private fun makeTestDataSourceProperties(): Properties {
        val props = Properties()
        props.setProperty("dataSourceClassName", "org.h2.jdbcx.JdbcDataSource")
        props.setProperty("dataSource.url", "jdbc:h2:mem:${dbId}_persistence;LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE")
        props.setProperty("dataSource.user", "sa")
        props.setProperty("dataSource.password", "")
        return props
    }
}
