package com.r3.corda.networkmanage.doorman

import com.r3.corda.networkmanage.common.DOORMAN_DB_NAME
import com.r3.corda.networkmanage.common.networkMapInMemoryH2DataSourceConfig
import com.r3.corda.networkmanage.common.utils.CertPathAndKey
import com.r3.corda.networkmanage.doorman.signer.LocalSigner
import net.corda.core.crypto.random63BitValue
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.internal.div
import net.corda.core.internal.readObject
import net.corda.core.messaging.ParametersUpdateInfo
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.nodeapi.internal.createDevNetworkMapCa
import net.corda.nodeapi.internal.crypto.CertificateAndKeyPair
import net.corda.nodeapi.internal.network.NETWORK_PARAMS_FILE_NAME
import net.corda.nodeapi.internal.network.SignedNetworkParameters
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.*
import net.corda.testing.driver.PortAllocation
import net.corda.testing.driver.internal.NodeHandleInternal
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.createDevIntermediateCaCertPath
import net.corda.testing.internal.toDatabaseSchemaName
import net.corda.testing.node.internal.CompatibilityZoneParams
import net.corda.testing.node.internal.internalDriver
import net.corda.testing.node.internal.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import org.junit.Assert.assertEquals
import java.net.URL
import java.security.cert.X509Certificate
import java.time.Instant

class NetworkParametersUpdateTest : IntegrationTest() {
    companion object {
        private val timeoutMillis = 5.seconds.toMillis()
        @ClassRule
        @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(),
                DUMMY_NOTARY_NAME.toDatabaseSchemaName(), DOORMAN_DB_NAME)
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
    fun `network parameters update commnunicated to node`() {
        // Initialise the server with some network parameters
        val initialNetParams = NetworkParametersCmd.Set(
                notaries = emptyList(),
                minimumPlatformVersion = 1,
                maxMessageSize = 1_000_000,
                maxTransactionSize = 1_000_000,
                parametersUpdate = null,
                eventHorizonDays = 30
        )
        applyNetworkParametersAndStart(initialNetParams)

        val compatibilityZone = CompatibilityZoneParams(
                URL("http://$serverAddress"),
                publishNotaries = {},
                rootCert = rootCaCert
        )

        internalDriver(
                portAllocation = portAllocation,
                compatibilityZone = compatibilityZone,
                notarySpecs = emptyList(),
                initialiseSerialization = false,
                extraCordappPackagesToScan = listOf("net.corda.finance")
        ) {
            var (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME),
                    startNode(providedName = BOB_NAME)
            ).map { it.getOrThrow() as NodeHandleInternal }

            // Make sure that stopping Bob doesn't remove him from the network map
            bob.stop()
            Thread.sleep(timeoutMillis * 2)
            assertThat(alice.rpc.networkMapSnapshot().map { it.legalIdentities[0].name }).contains(BOB_NAME)

            val snapshot = alice.rpc.networkParametersFeed().snapshot
            val updates = alice.rpc.networkParametersFeed().updates.bufferUntilSubscribed()
            assertThat(snapshot).isNull()

            val updateDeadline = Instant.now() + 10.seconds

            applyNetworkParametersAndStart(initialNetParams.copy(
                    maxTransactionSize = 1_000_001,
                    parametersUpdate = ParametersUpdateConfig(
                            description = "Very Important Update",
                            updateDeadline = updateDeadline
                    )
            ))

            updates.expectEvents(isStrict = true) {
                sequence(
                        expect { update: ParametersUpdateInfo ->
                            assertEquals(update.description, "Very Important Update")
                            assertEquals(update.parameters.maxTransactionSize, 1_000_001)
                            assertEquals(update.parameters.epoch, 2) // The epoch must increment automatically.
                        }
                )
            }

            val paramUpdateInfo = alice.rpc.networkParametersFeed().snapshot!!
            alice.rpc.acceptNewNetworkParameters(paramUpdateInfo.hash)

            // Make sure we've passed the deadline
            Thread.sleep(Math.max(updateDeadline.toEpochMilli() - System.currentTimeMillis(), 0))
            applyNetworkParametersAndStart(NetworkParametersCmd.FlagDay)

            alice.stop()
            alice = startNode(providedName = ALICE_NAME).getOrThrow() as NodeHandleInternal

            // TODO It is also possible to check what version of parameters node runs by writing flow that reads that value from ServiceHub
            val networkParameters = (alice.configuration.baseDirectory / NETWORK_PARAMS_FILE_NAME)
                    .readObject<SignedNetworkParameters>().verified()
            assertEquals(networkParameters, paramUpdateInfo.parameters)
            assertThat(alice.rpc.networkParametersFeed().snapshot).isNull() // Check that NMS doesn't advertise updates anymore.
            // Check that Bob is no longer on the network as it didn't accept the new parameteres.
            assertThat(alice.rpc.networkMapSnapshot().map { it.legalIdentities[0].name }).doesNotContain(BOB_NAME)
        }
    }

    private fun startServer(startNetworkMap: Boolean = true): NetworkManagementServer {
        val doormanConfig = DoormanConfig(approveAll = true, jira = null, approveInterval = timeoutMillis)
        val server = NetworkManagementServer(makeTestDataSourceProperties(DOORMAN_DB_NAME, dbNamePostfix, fallBackConfigSupplier = ::networkMapInMemoryH2DataSourceConfig), DatabaseConfig(runMigration = true), doormanConfig, null)
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
        NetworkManagementServer(
                makeTestDataSourceProperties(DOORMAN_DB_NAME, dbNamePostfix, fallBackConfigSupplier = ::networkMapInMemoryH2DataSourceConfig),
                DatabaseConfig(runMigration = true),
                DoormanConfig(approveAll = true, jira = null, approveInterval = timeoutMillis),
                null).use {
            it.netParamsUpdateHandler.processNetworkParameters(networkParametersCmd)
        }
        server = startServer(startNetworkMap = true)
        // Wait for server to process the parameters update and for the nodes to poll again
        Thread.sleep(timeoutMillis * 2)
    }
}
