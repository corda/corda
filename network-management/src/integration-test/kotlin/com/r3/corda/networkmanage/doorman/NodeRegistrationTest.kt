/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.DOORMAN_DB_NAME
import com.r3.corda.networkmanage.common.networkMapInMemoryH2DataSourceConfig
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.cordform.CordformNode
import net.corda.core.crypto.random63BitValue
import net.corda.core.identity.CordaX500Name
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
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.internal.CompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import net.corda.testing.node.internal.makeTestDataSourceProperties
import net.corda.testing.node.internal.makeTestDatabaseProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import java.net.URL
import java.security.cert.X509Certificate
import kotlin.streams.toList

// This is the same test as the one in net.corda.node.utilities.registration but using the real doorman and with some
// extra checks on the network map.
class NodeRegistrationTest : IntegrationTest() {
    companion object {
        private val notaryName = CordaX500Name("NotaryService", "Zurich", "CH")
        private val aliceName = CordaX500Name("Alice", "London", "GB")
        private val genevieveName = CordaX500Name("Genevieve", "London", "GB")
        private val timeoutMillis = 5.seconds.toMillis()

        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(notaryName.organisation, aliceName.organisation, genevieveName.organisation,
                DOORMAN_DB_NAME)
    }

    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule(true)

    private val portAllocation = PortAllocation.Incremental(10000)
    private val serverAddress = portAllocation.nextHostAndPort()

    private lateinit var rootCaCert: X509Certificate
    private lateinit var doormanCa: CertificateAndKeyPair
    private lateinit var networkMapCa: CertificateAndKeyPair
    //for normal integration tests (not against standalone db) to create a unique db name for each tests against H2
    private lateinit var dbNamePostfix: String

    private var server: NetworkManagementServer? = null

    private val doormanConfig: DoormanConfig get() = DoormanConfig(approveAll = true, jira = null, approveInterval = timeoutMillis)
    private val revocationConfig: CertificateRevocationConfig
        get() = CertificateRevocationConfig(
                approveAll = true,
                jira = null,
                approveInterval = timeoutMillis,
                crlCacheTimeout = timeoutMillis,
                localSigning = CertificateRevocationConfig.LocalSigning(
                        crlEndpoint = URL("http://test.com/crl"),
                        crlUpdateInterval = timeoutMillis)
        )

    @Before
    fun init() {
        dbNamePostfix = random63BitValue().toString()
        val (rootCa, doormanCa) = createDevIntermediateCaCertPath()
        rootCaCert = rootCa.certificate
        this.doormanCa = doormanCa
        networkMapCa = createDevNetworkMapCa(rootCa)
    }

    @After
    fun cleanUp() {
        server?.close()
    }

    @Test
    fun `register nodes with doorman and then they transact with each other`() {
        // Start the server without the network parameters config which won't start the network map. Just the doorman
        // registration process will start up, allowing us to register the notaries which will then be used in the network
        // parameters.
        server = startServer(startNetworkMap = false)
        val compatibilityZone = CompatibilityZoneParams(
                URL("http://$serverAddress"),
                publishNotaries = { notaryInfos ->
                    val setNetParams = NetworkParametersCmd.Set(
                            notaries = notaryInfos,
                            minimumPlatformVersion = 1,
                            maxMessageSize = 10485760,
                            maxTransactionSize = 10485760,
                            parametersUpdate = null,
                            eventHorizonDays = 30
                    )
                    // Restart the server once we're able to generate the network parameters
                    applyNetworkParametersAndStart(setNetParams)
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
            ).map { it.getOrThrow() as NodeHandleInternal }

            alice.onlySeesFromNetworkMap(alice, notary)
            notary.onlySeesFromNetworkMap(alice, notary)

            val genevieve = startNode(providedName = genevieveName).getOrThrow() as NodeHandleInternal

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

    private fun NodeHandleInternal.onlySeesFromNetworkMap(vararg nodes: NodeHandle) {
        // Make sure the nodes aren't getting the node infos from their additional directories
        val nodeInfosDir = configuration.baseDirectory / CordformNode.NODE_INFO_DIRECTORY
        if (nodeInfosDir.exists()) {
            assertThat(nodeInfosDir.list { it.toList() }).isEmpty()
        }
        assertThat(rpc.networkMapSnapshot()).containsOnlyElementsOf(nodes.map { it.nodeInfo })
    }

    private fun startServer(startNetworkMap: Boolean = true): NetworkManagementServer {
        val server = NetworkManagementServer(makeTestDataSourceProperties(DOORMAN_DB_NAME, dbNamePostfix, fallBackConfigSupplier = ::networkMapInMemoryH2DataSourceConfig), makeTestDatabaseProperties(DOORMAN_DB_NAME), doormanConfig, revocationConfig)
        server.start(
                serverAddress,
                CertPathAndKey(listOf(doormanCa.certificate, rootCaCert), doormanCa.keyPair.private),
                if (startNetworkMap) {
                    NetworkMapStartParams(
                            LocalSigner(networkMapCa),
                            NetworkMapConfig(cacheTimeout = timeoutMillis, signInterval = timeoutMillis)
                    )
                } else {
                    null
                }
        )
        return server
    }

    private fun applyNetworkParametersAndStart(networkParametersCmd: NetworkParametersCmd) {
        server?.close()
        NetworkManagementServer(makeTestDataSourceProperties(DOORMAN_DB_NAME, dbNamePostfix, fallBackConfigSupplier = ::networkMapInMemoryH2DataSourceConfig), makeTestDatabaseProperties(DOORMAN_DB_NAME), doormanConfig, revocationConfig).use {
            it.netParamsUpdateHandler.processNetworkParameters(networkParametersCmd)
        }
        server = startServer(startNetworkMap = true)
        // Wait for server to process the parameters update and for the nodes to poll again
        Thread.sleep(timeoutMillis * 2)
    }
}
