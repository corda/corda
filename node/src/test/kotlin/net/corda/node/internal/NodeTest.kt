package net.corda.node.internal

import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.whenever
import net.corda.core.identity.CordaX500Name
import net.corda.core.internal.delete
import net.corda.core.internal.list
import net.corda.core.internal.readObject
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.serialize
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.node.VersionInfo
import net.corda.node.internal.schemas.NodeInfoSchemaV1
import net.corda.node.services.config.NodeConfiguration
import net.corda.nodeapi.internal.SignedNodeInfo
import net.corda.nodeapi.internal.network.NodeInfoFilesCopier.Companion.NODE_INFO_FILE_NAME_PREFIX
import net.corda.nodeapi.internal.persistence.CordaPersistence
import net.corda.nodeapi.internal.persistence.DatabaseConfig
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.SerializationEnvironmentRule
import net.corda.testing.internal.createNodeInfoAndSigned
import net.corda.testing.internal.rigorousMock
import net.corda.testing.node.MockServices.Companion.makeTestDataSourceProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NodeTest {
    private abstract class AbstractNodeConfiguration : NodeConfiguration

    @Rule
    @JvmField
    val temporaryFolder = TemporaryFolder()
    @Rule
    @JvmField
    val testSerialization = SerializationEnvironmentRule()

    private fun nodeInfoFile(): Path? {
        return temporaryFolder.root.toPath().list { paths ->
            paths.filter { it.fileName.toString().startsWith(NODE_INFO_FILE_NAME_PREFIX) }.findAny().orElse(null)
        }
    }

    private fun AbstractNode.generateNodeInfo(): NodeInfo {
        assertNull(nodeInfoFile())
        generateAndSaveNodeInfo()
        val path = nodeInfoFile()!!
        try {
            return path.readObject<SignedNodeInfo>().verified()
        } finally {
            path.delete()
        }
    }

    @Test
    fun `generateAndSaveNodeInfo works`() {
        val configuration = createConfig()
        val platformVersion = 789
        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use { database ->
            val node = Node(configuration, rigorousMock<VersionInfo>().also {
                doReturn(platformVersion).whenever(it).platformVersion
            }, initialiseSerialization = false)
            assertEquals(node.generateNodeInfo(), node.generateNodeInfo())  // Node info doesn't change (including the serial)
        }
    }

    @Test
    fun `clear network map cache works`() {
        val configuration = createConfig()
        val (nodeInfo, _) = createNodeInfoAndSigned(ALICE_NAME)
        configureDatabase(configuration.dataSourceProperties, configuration.database, { null }, { null }).use {
            it.transaction {
                val persistentNodeInfo = NodeInfoSchemaV1.PersistentNodeInfo(
                        id = 0,
                        hash = nodeInfo.serialize().hash.toString(),
                        addresses = nodeInfo.addresses.map { NodeInfoSchemaV1.DBHostAndPort.fromHostAndPort(it) },
                        legalIdentitiesAndCerts = nodeInfo.legalIdentitiesAndCerts.mapIndexed { idx, elem ->
                            NodeInfoSchemaV1.DBPartyAndCertificate(elem, isMain = idx == 0)
                        },
                        platformVersion = nodeInfo.platformVersion,
                        serial = nodeInfo.serial
                )
                // Save some NodeInfo
                session.save(persistentNodeInfo)
            }
            val node = Node(configuration, rigorousMock<VersionInfo>().also {
                doReturn(10).whenever(it).platformVersion
            }, initialiseSerialization = false)
            assertThat(getAllInfos(it)).isNotEmpty
            node.clearNetworkMapCache()
            assertThat(getAllInfos(it)).isEmpty()
        }
    }

    private fun getAllInfos(database: CordaPersistence): List<NodeInfoSchemaV1.PersistentNodeInfo> {
        return database.transaction {
            val criteria = session.criteriaBuilder.createQuery(NodeInfoSchemaV1.PersistentNodeInfo::class.java)
            criteria.select(criteria.from(NodeInfoSchemaV1.PersistentNodeInfo::class.java))
            session.createQuery(criteria).resultList
        }
    }

    private fun createConfig(): NodeConfiguration {
        val dataSourceProperties = makeTestDataSourceProperties()
        val databaseConfig = DatabaseConfig()
        val nodeAddress = NetworkHostAndPort("0.1.2.3", 456)
        val nodeName = CordaX500Name("Manx Blockchain Corp", "Douglas", "IM")
        return rigorousMock<AbstractNodeConfiguration>().also {
            doReturn(nodeAddress).whenever(it).p2pAddress
            doReturn(nodeName).whenever(it).myLegalName
            doReturn(null).whenever(it).notary // Don't add notary identity.
            doReturn(dataSourceProperties).whenever(it).dataSourceProperties
            doReturn(databaseConfig).whenever(it).database
            doReturn(temporaryFolder.root.toPath()).whenever(it).baseDirectory
            doReturn(true).whenever(it).devMode // Needed for identity cert.
            doReturn("tsp").whenever(it).trustStorePassword
            doReturn("ksp").whenever(it).keyStorePassword
        }
    }
}
