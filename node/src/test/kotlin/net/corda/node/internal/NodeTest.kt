package net.corda.node.internal

import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.readObject
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.coretesting.internal.createNodeInfoAndSigned
import net.corda.coretesting.internal.rigorousMock
import net.corda.node.VersionInfo
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.config.FlowOverrideConfig
import net.corda.node.services.config.FlowTimeoutConfiguration
import net.corda.node.services.config.NodeConfigurationImpl
import net.corda.node.services.config.NodeRpcSettings
import net.corda.node.services.config.TelemetryConfiguration
import net.corda.node.services.config.VerifierType
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.configureDatabase
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeTest {
    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private fun nodeInfoFile(): Path? {
        return temporaryFolder.root.toPath().useDirectoryEntries { paths ->
            paths.find { it.name.startsWith(NODE_INFO_FILE_NAME_PREFIX) }
        }
    }

    private fun Node.generateNodeInfo(): NodeInfo {
        assertNull(nodeInfoFile())
        generateAndSaveNodeInfo()
        val path = nodeInfoFile()!!
        try {
            return path.readObject<SignedNodeInfo>().verified()
        } finally {
            path.deleteExisting()
        }
    }

    @Test(timeout=300_000)
	fun `generateAndSaveNodeInfo works`() {
        val configuration = createConfig(ALICE_NAME)
        val info = VersionInfo(789, "3.0", "SNAPSHOT", "R3")
        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use {
            val node = Node(configuration, info, initialiseSerialization = false)
            assertEquals(node.generateNodeInfo(), node.generateNodeInfo())  // Node info doesn't change (including the serial)
        }
    }

    @Test(timeout=300_000)
    fun `check node service availability`() {
        val configuration = createConfig(ALICE_NAME)
        val info = VersionInfo(789, "3.0", "SNAPSHOT", "R3")
        val node = Node(configuration, info, initialiseSerialization = false)
        // Regular nodes must not have internal access to the notary service
        assertNull(node.services.notaryService)
    }

    @Test(timeout=300_000)
	fun `clear network map cache works`() {
        val configuration = createConfig(ALICE_NAME)
        val (nodeInfo, _) = createNodeInfoAndSigned(ALICE_NAME)
        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use {
            it.transaction {
                val persistentNodeInfo = NodeInfoSchemaV1.PersistentNodeInfo(
                        id = 0,
                        hash = nodeInfo.serialize().hash.toString(),
                        addresses = nodeInfo.addresses.map(NodeInfoSchemaV1.DBHostAndPort::fromHostAndPort),
                        legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                            NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                        },
                        platformVersion = nodeInfo.platformVersion,
                        serial = nodeInfo.serial
                )
                // Save some NodeInfo
                session.save(persistentNodeInfo)
            }
            val versionInfo = VersionInfo(10, "3.0", "SNAPSHOT", "R3")
            val node = Node(configuration, versionInfo, initialiseSerialization = false)
            assertThat(getAllInfos(it)).isNotEmpty
            node.clearNetworkMapCache()
            assertThat(getAllInfos(it)).isEmpty()
        }
    }

    @Test(timeout=300_000)
	fun `Node can start with multiple keypairs for its identity`() {
        val configuration = createConfig(ALICE_NAME)
        val (nodeInfo1, _) = createNodeInfoAndSigned(ALICE_NAME)
        val (nodeInfo2, _) = createNodeInfoAndSigned(ALICE_NAME)


        val persistentNodeInfo2 = NodeInfoSchemaV1.PersistentNodeInfo(
                id = 0,
                hash = nodeInfo2.serialize().hash.toString(),
                addresses = nodeInfo2.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                legalIdentitiesAndCerts = nodeInfo2.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                    NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                },
                platformVersion = nodeInfo2.platformVersion,
                serial = nodeInfo2.serial
        )

        val persistentNodeInfo1 = NodeInfoSchemaV1.PersistentNodeInfo(
                id = 0,
                hash = nodeInfo1.serialize().hash.toString(),
                addresses = nodeInfo1.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                legalIdentitiesAndCerts = nodeInfo1.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                    NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                },
                platformVersion = nodeInfo1.platformVersion,
                serial = nodeInfo1.serial
        )

        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use { persistence ->
            persistence.transaction {
                session.save(persistentNodeInfo1)
            }
            persistence.transaction {
                session.save(persistentNodeInfo2)
            }

            val node = Node(configuration, rigorousMock<VersionInfo>().also {
                doReturn(10).whenever(it).platformVersion
                doReturn("test-vendor").whenever(it).vendor
                doReturn("1.0").whenever(it).releaseVersion
            }, initialiseSerialization = false)

            //this throws an exception with old behaviour
            node.generateNodeInfo()
        }
    }

    private fun getAllInfos(database: CordaPersistence): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        return database.transaction {
            val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java)
            criteria.select(criteria.from(NodeInfoSchemaV1.PersistentNodeInfo::class.java))
            session.createQuery(criteria).resultList
        }
    }

    private fun createConfig(nodeName: CordaX500Name): NodeConfigurationImpl {
        val fakeAddress = NetworkHostAndPort("0.1.2.3", 456)
        return NodeConfigurationImpl(
                baseDirectory = temporaryFolder.root.toPath(),
                myLegalName = nodeName,
                devMode = true, // Needed for identity cert.
                emailAddress = "",
                p2pAddress = fakeAddress,
                keyStorePassword = "ksp",
                trustStorePassword = "tsp",
                crlCheckSoftFail = true,
                dataSourceProperties = makeTestDataSourceProperties(),
                database = DatabaseConfig(),
                rpcUsers = emptyList(),
                verifierType = VerifierType.InMemory,
                flowTimeout = FlowTimeoutConfiguration(timeout = Duration.ZERO, backoffBase = 1.0, maxRestartCount = 1),
                telemetry = TelemetryConfiguration(openTelemetryEnabled = true, simpleLogTelemetryEnabled = false, spanStartEndEventsEnabled = false, copyBaggageToTags = false),
                rpcSettings = NodeRpcSettings(address = fakeAddress, adminAddress = null, ssl = null),
                messagingServerAddress = null,
                notary = null,
                flowOverrides = FlowOverrideConfig(listOf()),
                configurationWithOptions = mock()
        )
    }
}
